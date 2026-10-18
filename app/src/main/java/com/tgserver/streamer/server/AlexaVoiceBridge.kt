package com.tgserver.streamer.server

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "AlexaVoiceBridge"
private const val MULTICAST_IP = "239.255.255.250"
private const val SSDP_PORT = 1900

/**
 * Alexa Voice Control Bridge using Belkin WeMo Smart Switch UPnP emulation.
 *
 * Enables Amazon Echo devices on the local Wi-Fi network to discover NoxMedia
 * as a native smart switch without requiring an Amazon Developer account, AWS Lambda,
 * or cloud subscriptions.
 */
class AlexaVoiceBridge(
    private val context: Context,
    private val httpPort: Int = 7790,
    private val deviceNameProvider: () -> String,
    private val isRunningProvider: () -> Boolean,
    private val onStateChange: (Boolean) -> Unit
) {
    private var httpServer: WeMoHttpServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var ssdpJob: Job? = null
    private var ssdpSocket: MulticastSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Generate consistent unique serial per physical device
    private val serialNumber: String by lazy {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank()) {
                "NOX" + androidId.takeLast(8).uppercase()
            } else {
                "NOX7788A1B2"
            }
        } catch (_: Exception) {
            "NOX7788A1B2"
        }
    }

    private val deviceUuid: String by lazy {
        "Socket-1_0-$serialNumber"
    }

    @Synchronized
    fun start() {
        if (httpServer != null) return

        Log.i(TAG, "Starting Alexa Voice Control bridge on port $httpPort (Device: ${deviceNameProvider()})")

        // 1. Acquire Wi-Fi Multicast lock to receive SSDP broadcast packets on Android
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("NoxMediaAlexaLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        // 2. Start lightweight local HTTP server for UPnP / WeMo descriptors & SOAP actions
        try {
            httpServer = WeMoHttpServer(httpPort).apply {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
            Log.i(TAG, "Alexa UPnP HTTP server listening on port $httpPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Alexa UPnP HTTP server on port $httpPort", e)
        }

        // 3. Start SSDP UDP listener for Alexa "Discover Devices" M-SEARCH packets
        startSsdpListener()
    }

    @Synchronized
    fun stop() {
        Log.i(TAG, "Stopping Alexa Voice Control bridge...")
        ssdpJob?.cancel()
        ssdpJob = null

        try {
            ssdpSocket?.let { s ->
                val group = InetAddress.getByName(MULTICAST_IP)
                s.leaveGroup(group)
                s.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing SSDP socket: ${e.message}")
        }
        ssdpSocket = null

        try {
            httpServer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Alexa HTTP server: ${e.message}")
        }
        httpServer = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing multicast lock: ${e.message}")
        }
        multicastLock = null
    }

    private fun startSsdpListener() {
        ssdpJob?.cancel()
        ssdpJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val socket = MulticastSocket(SSDP_PORT).apply {
                        reuseAddress = true
                        timeToLive = 4
                        receiveBufferSize = 65536
                        val group = InetAddress.getByName(MULTICAST_IP)
                        joinGroup(group)
                    }
                    ssdpSocket = socket

                    val buffer = ByteArray(2048)
                    Log.i(TAG, "Listening for Alexa SSDP M-SEARCH discovery on 239.255.255.250:$SSDP_PORT")

                    while (isActive && !socket.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                            val message = String(packet.data, 0, packet.length, Charsets.UTF_8)

                            if (message.startsWith("M-SEARCH", ignoreCase = true)) {
                                handleMSearch(message, packet.address, packet.port, socket)
                            }
                        } catch (e: Exception) {
                            if (!isActive || socket.isClosed) break
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "SSDP listener loop error: ${e.message}. Rebinding in 5s...")
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun handleMSearch(
        message: String,
        remoteAddress: InetAddress,
        remotePort: Int,
        socket: MulticastSocket
    ) {
        val st = message.lines()
            .firstOrNull { it.trim().startsWith("ST:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?: return

        // Alexa searches for Belkin devices using any of these ST targets
        val matches = st.equals("ssdp:all", ignoreCase = true) ||
                st.contains("urn:Belkin:device:**", ignoreCase = true) ||
                st.contains("urn:Belkin:device:controllee:1", ignoreCase = true) ||
                st.contains("urn:Belkin:service:basicevent:1", ignoreCase = true) ||
                st.equals("upnp:rootdevice", ignoreCase = true)

        if (!matches) return

        val localIp = LocalHttpServer.getLocalIpAddress()
        if (localIp.isBlank() || localIp == "127.0.0.1") return

        val targetSt = if (st.equals("upnp:rootdevice", ignoreCase = true)) {
            "upnp:rootdevice"
        } else {
            "urn:Belkin:device:controllee:1"
        }

        val targetUsn = if (st.equals("upnp:rootdevice", ignoreCase = true)) {
            "uuid:$deviceUuid::upnp:rootdevice"
        } else {
            "uuid:$deviceUuid::urn:Belkin:device:controllee:1"
        }

        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dateHeader = sdf.format(Date())

        val response = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=86400\r\n" +
                "DATE: $dateHeader\r\n" +
                "EXT:\r\n" +
                "LOCATION: http://$localIp:$httpPort/setup.xml\r\n" +
                "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
                "01-NLS: $serialNumber\r\n" +
                "SERVER: Unspecified, UPnP/1.0, Unspecified\r\n" +
                "X-User-Agent: redsonic\r\n" +
                "ST: $targetSt\r\n" +
                "USN: $targetUsn\r\n\r\n"

        try {
            val bytes = response.toByteArray(Charsets.UTF_8)
            val sendPacket = DatagramPacket(bytes, bytes.size, remoteAddress, remotePort)
            socket.send(sendPacket)
            Log.d(TAG, "Sent WeMo M-SEARCH response to Alexa at ${remoteAddress.hostAddress}:$remotePort")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send M-SEARCH response: ${e.message}")
        }
    }

    /**
     * NanoHTTPD instance serving WeMo XML files and handling SOAP control actions.
     */
    private inner class WeMoHttpServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimEnd('/')
            val method = session.method

            if (method == Method.HEAD || method == Method.OPTIONS) {
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            }

            return when {
                uri.isEmpty() || uri == "/" || uri.endsWith("/setup.xml") -> {
                    serveSetupXml()
                }
                uri.endsWith("/eventservice.xml") -> {
                    serveEventServiceXml()
                }
                uri.endsWith("/upnp/control/basicevent1") -> {
                    handleSoapAction(session)
                }
                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            }
        }

        private fun serveSetupXml(): Response {
            val friendlyName = deviceNameProvider().ifBlank { "NoxMedia" }
            val isRunning = if (isRunningProvider()) 1 else 0

            val xml = """<?xml version="1.0"?>
<root xmlns="urn:Belkin:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <device>
    <deviceType>urn:Belkin:device:controllee:1</deviceType>
    <friendlyName>$friendlyName</friendlyName>
    <manufacturer>Belkin</manufacturer>
    <manufacturerURL>http://www.belkin.com</manufacturerURL>
    <modelDescription>Belkin Plugin Socket 1.0</modelDescription>
    <modelName>Socket</modelName>
    <modelNumber>3.1415</modelNumber>
    <modelURL>http://www.belkin.com/plugin/</modelURL>
    <serialNumber>$serialNumber</serialNumber>
    <UDN>uuid:$deviceUuid</UDN>
    <UPC>123456789</UPC>
    <binaryState>$isRunning</binaryState>
    <serviceList>
      <service>
        <serviceType>urn:Belkin:service:basicevent:1</serviceType>
        <serviceId>urn:Belkin:serviceId:basicevent1</serviceId>
        <controlURL>/upnp/control/basicevent1</controlURL>
        <eventSubURL>/upnp/event/basicevent1</eventSubURL>
        <SCPDURL>/eventservice.xml</SCPDURL>
      </service>
    </serviceList>
  </device>
</root>""".trimIndent()

            val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xml)
            resp.addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            return resp
        }

        private fun serveEventServiceXml(): Response {
            val xml = """<?xml version="1.0"?>
<scpd xmlns="urn:Belkin:service-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <actionList>
    <action>
      <name>SetBinaryState</name>
      <argumentList>
        <argument>
          <name>BinaryState</name>
          <direction>in</direction>
          <relatedStateVariable>BinaryState</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
    <action>
      <name>GetBinaryState</name>
      <argumentList>
        <argument>
          <name>BinaryState</name>
          <direction>out</direction>
          <relatedStateVariable>BinaryState</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="yes">
      <name>BinaryState</name>
      <dataType>Boolean</dataType>
      <defaultValue>0</defaultValue>
    </stateVariable>
  </serviceStateTable>
</scpd>""".trimIndent()

            val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xml)
            resp.addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            return resp
        }

        private fun handleSoapAction(session: IHTTPSession): Response {
            val postBody = try {
                val map = HashMap<String, String>()
                session.parseBody(map)
                map["postData"] ?: ""
            } catch (e: Exception) {
                ""
            }

            Log.i(TAG, "Received Alexa SOAP action request: ${postBody.take(120)}")

            // Check if SetBinaryState
            if (postBody.contains("SetBinaryState", ignoreCase = true)) {
                val match = Regex("<BinaryState>([01])</BinaryState>", RegexOption.IGNORE_CASE).find(postBody)
                val targetState = match?.groupValues?.get(1) == "1"

                Log.i(TAG, "Alexa instructed state change: TURN ${if (targetState) "ON" else "OFF"}")
                onStateChange(targetState)

                val stateValue = if (targetState) 1 else 0
                val xmlResponse = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>$stateValue</BinaryState>
    </u:SetBinaryStateResponse>
  </s:Body>
</s:Envelope>""".trimIndent()

                val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xmlResponse)
                resp.addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                return resp
            }

            // Check if GetBinaryState
            val currentState = if (isRunningProvider()) 1 else 0
            val xmlResponse = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>$currentState</BinaryState>
    </u:GetBinaryStateResponse>
  </s:Body>
</s:Envelope>""".trimIndent()

            val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xmlResponse)
            resp.addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            return resp
        }
    }
}
