package com.tgserver.streamer.server

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "AlexaVoiceBridge"
private const val MULTICAST_IP = "239.255.255.250"
private const val SSDP_PORT = 1900

/**
 * Universal Local Alexa Voice Control Bridge.
 *
 * Implements dual local protocols supported natively by Amazon Echo hardware:
 * 1. Belkin WeMo Smart Switch UPnP emulation
 * 2. Philips Hue Bridge V1 emulation
 *
 * Enables Amazon Echo devices across all generations (Echo Dot, Echo Show, Echo Studio)
 * on local Wi-Fi to discover and control NoxMedia without cloud subscriptions,
 * AWS Lambda, or custom skills.
 */
class AlexaVoiceBridge(
    private val context: Context,
    private val httpPort: Int = 7790,
    private val deviceNameProvider: () -> String,
    private val isRunningProvider: () -> Boolean,
    private val onStateChange: (Boolean) -> Unit
) {
    private var httpServer: DualProtocolHttpServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var ssdpJob: Job? = null
    private var ssdpSocket: MulticastSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Unique serial per device (Belkin standard 14-char format e.g. 221517K...)
    private val serialNumber: String by lazy {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank()) {
                val clean = androidId.filter { it.isLetterOrDigit() }.takeLast(7).uppercase()
                "221517K$clean".padEnd(14, '0')
            } else {
                "221517K0101763"
            }
        } catch (_: Exception) {
            "221517K0101763"
        }
    }

    private val deviceUuid: String by lazy {
        "Socket-1_0-$serialNumber"
    }

    private val hueMac: String by lazy {
        // Philips Hue MACs strictly start with Philips OUI (00:17:88)
        val suffix = serialNumber.filter { it.isLetterOrDigit() }.takeLast(6).lowercase()
        "001788$suffix".padEnd(12, '0')
    }

    private val hueBridgeId: String by lazy {
        (hueMac.take(6) + "fffe" + hueMac.takeLast(6)).uppercase()
    }

    private var actualHttpPort: Int = httpPort

    @Synchronized
    fun start() {
        if (httpServer != null) return

        StreamLog.init(context)
        
        // Attempt port 80 if device has root/permissions, else fallback to httpPort
        val candidatePort = try {
            java.net.ServerSocket(80).use { 80 }
        } catch (_: Exception) {
            httpPort
        }
        actualHttpPort = candidatePort

        StreamLog.i(TAG, "Starting Alexa Voice Bridge on port $actualHttpPort (Device Name: \"${deviceNameProvider()}\")")

        // 1. Acquire Wi-Fi Multicast lock
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("NoxMediaAlexaMulticastLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            StreamLog.i(TAG, "Acquired Wi-Fi MulticastLock")
        } catch (e: Exception) {
            StreamLog.w(TAG, "Failed to acquire MulticastLock: ${e.message}")
        }

        // 2. Start lightweight local HTTP server for UPnP / WeMo / Hue descriptors
        try {
            httpServer = DualProtocolHttpServer(actualHttpPort).apply {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
            StreamLog.i(TAG, "Alexa HTTP server listening on http://0.0.0.0:$actualHttpPort")
        } catch (e: Exception) {
            StreamLog.e(TAG, "Failed to start Alexa HTTP server on port $actualHttpPort: ${e.message}")
        }

        // 3. Start SSDP UDP listener on 239.255.255.250:1900
        startSsdpListener()
    }

    @Synchronized
    fun stop() {
        StreamLog.i(TAG, "Stopping Alexa Voice Bridge...")
        ssdpJob?.cancel()
        ssdpJob = null

        try {
            ssdpSocket?.let { s ->
                val group = InetAddress.getByName(MULTICAST_IP)
                val groupAddr = InetSocketAddress(group, SSDP_PORT)
                try {
                    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                    for (netIf in interfaces) {
                        if (!netIf.isLoopback && netIf.isUp && netIf.supportsMulticast()) {
                            try { s.leaveGroup(groupAddr, netIf) } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                s.close()
            }
        } catch (e: Exception) {
            StreamLog.w(TAG, "Error closing SSDP socket: ${e.message}")
        }
        ssdpSocket = null

        try {
            httpServer?.stop()
        } catch (e: Exception) {
            StreamLog.w(TAG, "Error stopping Alexa HTTP server: ${e.message}")
        }
        httpServer = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            StreamLog.w(TAG, "Error releasing MulticastLock: ${e.message}")
        }
        multicastLock = null
    }

    private fun startSsdpListener() {
        ssdpJob?.cancel()
        ssdpJob = coroutineScope.launch {
            while (isActive) {
                try {
                    // MUST create unbound socket first, enable SO_REUSEADDR, and then bind to SSDP_PORT
                    val socket = MulticastSocket(null).apply {
                        reuseAddress = true
                        timeToLive = 4
                        receiveBufferSize = 65536
                        bind(InetSocketAddress(SSDP_PORT))
                    }
                    ssdpSocket = socket

                    // Join group explicitly on active multicast network interfaces (wlan0, etc.)
                    val group = InetAddress.getByName(MULTICAST_IP)
                    val groupAddr = InetSocketAddress(group, SSDP_PORT)
                    var joinedCount = 0
                    try {
                        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                        for (netIf in interfaces) {
                            if (!netIf.isLoopback && netIf.isUp && netIf.supportsMulticast()) {
                                try {
                                    socket.joinGroup(groupAddr, netIf)
                                    StreamLog.i(TAG, "Joined SSDP group on interface: ${netIf.name}")
                                    joinedCount++
                                } catch (e: Exception) {
                                    StreamLog.w(TAG, "Could not join SSDP on ${netIf.name}: ${e.message}")
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (joinedCount == 0) {
                        try {
                            socket.joinGroup(group)
                            StreamLog.i(TAG, "Joined SSDP group on default interface")
                        } catch (e: Exception) {
                            StreamLog.w(TAG, "Could not join SSDP default interface: ${e.message}")
                        }
                    }

                    StreamLog.i(TAG, "SSDP listener active. Listening for Alexa discovery on 239.255.255.250:1900")

                    val buffer = ByteArray(4096)
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
                        StreamLog.w(TAG, "SSDP listener socket error: ${e.message}. Rebinding in 5s...")
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun getLocalIpForRemote(remoteAddress: InetAddress): String {
        try {
            val remoteBytes = remoteAddress.address
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val localBytes = addr.address
                        if (localBytes.size == 4 && remoteBytes.size == 4 &&
                            localBytes[0] == remoteBytes[0] && localBytes[1] == remoteBytes[1]
                        ) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return LocalHttpServer.getLocalIpAddress()
    }

    private fun handleMSearch(
        message: String,
        remoteAddress: InetAddress,
        remotePort: Int,
        socket: MulticastSocket
    ) {
        val lines = message.lines()
        val stLine = lines.firstOrNull { it.trim().startsWith("ST:", ignoreCase = true) }
        val st = stLine?.substringAfter(":")?.trim() ?: ""

        val isWeMoSearch = st.equals("ssdp:all", ignoreCase = true) ||
                st.contains("urn:Belkin:device:**", ignoreCase = true) ||
                st.contains("urn:Belkin:device:controllee:1", ignoreCase = true) ||
                st.contains("urn:Belkin:service:basicevent:1", ignoreCase = true) ||
                st.equals("upnp:rootdevice", ignoreCase = true)

        // Echo Dot (especially 2nd Gen) strictly requires Port 80 for Philips Hue discovery.
        // When running on non-80 port (e.g. 7790), advertising Hue on generic ssdp:all or upnp:rootdevice
        // causes Echo Dot to classify the IP as an invalid Hue bridge and discard discovery entirely.
        // Therefore, only advertise Hue on generic queries if bound to port 80.
        val isHueSearch = if (actualHttpPort == 80) {
            st.equals("ssdp:all", ignoreCase = true) ||
                    st.equals("upnp:rootdevice", ignoreCase = true) ||
                    st.contains("urn:schemas-upnp-org:device:basic:1", ignoreCase = true)
        } else {
            st.contains("urn:schemas-upnp-org:device:basic:1", ignoreCase = true)
        }

        if (!isWeMoSearch && !isHueSearch) return

        val localIp = getLocalIpForRemote(remoteAddress)
        if (localIp.isBlank() || localIp == "127.0.0.1") return

        StreamLog.i(TAG, "Alexa M-SEARCH detected from ${remoteAddress.hostAddress}:$remotePort (ST: $st)")

        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dateHeader = sdf.format(Date())

        // 1. Respond with WeMo UPnP if matching
        if (isWeMoSearch) {
            val targets = when {
                st.equals("ssdp:all", ignoreCase = true) -> listOf(
                    "upnp:rootdevice" to "uuid:$deviceUuid::upnp:rootdevice",
                    "urn:Belkin:device:controllee:1" to "uuid:$deviceUuid::urn:Belkin:device:controllee:1",
                    "urn:Belkin:device:**" to "uuid:$deviceUuid::urn:Belkin:device:**"
                )
                st.contains("urn:Belkin:device:controllee:1", ignoreCase = true) -> listOf(
                    "urn:Belkin:device:controllee:1" to "uuid:$deviceUuid::urn:Belkin:device:controllee:1"
                )
                st.contains("urn:Belkin:service:basicevent:1", ignoreCase = true) -> listOf(
                    "urn:Belkin:service:basicevent:1" to "uuid:$deviceUuid::urn:Belkin:service:basicevent:1"
                )
                st.contains("urn:Belkin:device:**", ignoreCase = true) -> listOf(
                    "urn:Belkin:device:**" to "uuid:$deviceUuid::urn:Belkin:device:**"
                )
                else -> listOf(
                    "upnp:rootdevice" to "uuid:$deviceUuid::upnp:rootdevice"
                )
            }

            for ((tSt, tUsn) in targets) {
                val response = "HTTP/1.1 200 OK\r\n" +
                        "CACHE-CONTROL: max-age=86400\r\n" +
                        "DATE: $dateHeader\r\n" +
                        "EXT:\r\n" +
                        "LOCATION: http://$localIp:$actualHttpPort/setup.xml\r\n" +
                        "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
                        "01-NLS: 1\r\n" +
                        "SERVER: Unspecified, UPnP/1.0, Unspecified\r\n" +
                        "ST: $tSt\r\n" +
                        "USN: $tUsn\r\n\r\n"

                sendUdpResponse(response.toByteArray(Charsets.UTF_8), remoteAddress, remotePort, socket)
            }
            StreamLog.i(TAG, "Sent WeMo SSDP (LOCATION: http://$localIp:$actualHttpPort/setup.xml) to ${remoteAddress.hostAddress}:$remotePort")
        }

        // 2. Respond with Philips Hue Bridge if matching
        if (isHueSearch) {
            val hueSt = if (st.equals("upnp:rootdevice", ignoreCase = true)) {
                "upnp:rootdevice"
            } else {
                "urn:schemas-upnp-org:device:basic:1"
            }
            val hueUsn = if (st.equals("upnp:rootdevice", ignoreCase = true)) {
                "uuid:2f402f80-da50-11e1-9b23-$hueMac::upnp:rootdevice"
            } else {
                "uuid:2f402f80-da50-11e1-9b23-$hueMac::urn:schemas-upnp-org:device:basic:1"
            }

            val hueResponse = "HTTP/1.1 200 OK\r\n" +
                    "CACHE-CONTROL: max-age=100\r\n" +
                    "EXT:\r\n" +
                    "LOCATION: http://$localIp:$actualHttpPort/description.xml\r\n" +
                    "SERVER: Linux/3.14.0 UPnP/1.0 IpBridge/1.24.0\r\n" +
                    "hue-bridgeid: $hueBridgeId\r\n" +
                    "ST: $hueSt\r\n" +
                    "USN: $hueUsn\r\n\r\n"

            sendUdpResponse(hueResponse.toByteArray(Charsets.UTF_8), remoteAddress, remotePort, socket)
            StreamLog.i(TAG, "Sent Hue SSDP (LOCATION: http://$localIp:$actualHttpPort/description.xml) to ${remoteAddress.hostAddress}:$remotePort")
        }
    }

    private fun sendUdpResponse(
        bytes: ByteArray,
        remoteAddress: InetAddress,
        remotePort: Int,
        multicastSocket: MulticastSocket
    ) {
        val packet = DatagramPacket(bytes, bytes.size, remoteAddress, remotePort)
        var sentViaMulticast = false
        try {
            multicastSocket.send(packet)
            sentViaMulticast = true
        } catch (e: Exception) {
            StreamLog.w(TAG, "MulticastSocket send error: ${e.message}")
        }

        // Ephemeral socket fallback only if multicast send failed
        if (!sentViaMulticast) {
            try {
                DatagramSocket().use { unicastSocket ->
                    unicastSocket.send(packet)
                }
            } catch (e: Exception) {
                StreamLog.w(TAG, "UnicastSocket fallback send error: ${e.message}")
            }
        }
    }

    /**
     * NanoHTTPD instance handling both WeMo UPnP/SOAP endpoints and Philips Hue REST API endpoints.
     */
    private inner class DualProtocolHttpServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimEnd('/')
            val method = session.method
            val clientIp = session.remoteIpAddress ?: "unknown"

            StreamLog.i(TAG, "Incoming Alexa HTTP $method $uri from $clientIp")

            if (method == Method.HEAD || method == Method.OPTIONS) {
                val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
                resp.addHeader("Access-Control-Allow-Origin", "*")
                resp.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                resp.addHeader("Access-Control-Allow-Headers", "Content-Type")
                return resp
            }

            return when {
                // ================= WeMo Endpoints =================
                uri.isEmpty() || uri == "/" || uri.endsWith("/setup.xml") -> {
                    serveSetupXml()
                }
                uri.endsWith("/eventservice.xml") -> {
                    serveEventServiceXml()
                }
                uri.endsWith("/upnp/control/basicevent1") -> {
                    handleWeMoSoapAction(session)
                }

                // ================= Philips Hue Endpoints =================
                uri.endsWith("/description.xml") -> {
                    serveHueDescriptionXml()
                }
                uri.startsWith("/api") -> {
                    handleHueApi(session, uri)
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            }
        }

        // --- WeMo Descriptors ---

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
    <manufacturer>Belkin International Inc.</manufacturer>
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

            StreamLog.i(TAG, "Served WeMo /setup.xml ($friendlyName)")
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

        private fun handleWeMoSoapAction(session: IHTTPSession): Response {
            val postBody = try {
                val map = HashMap<String, String>()
                session.parseBody(map)
                map["postData"] ?: ""
            } catch (e: Exception) {
                ""
            }

            StreamLog.i(TAG, "Received WeMo SOAP action: ${postBody.take(120)}")

            if (postBody.contains("SetBinaryState", ignoreCase = true)) {
                val match = Regex("<BinaryState>([01])</BinaryState>", RegexOption.IGNORE_CASE).find(postBody)
                val targetState = match?.groupValues?.get(1) == "1"

                StreamLog.i(TAG, "Alexa WeMo state change: TURN ${if (targetState) "ON" else "OFF"}")
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

            // GetBinaryState
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

        // --- Philips Hue Endpoints ---

        private fun serveHueDescriptionXml(): Response {
            val localIp = LocalHttpServer.getLocalIpAddress()
            val xml = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <URLBase>http://$localIp:$actualHttpPort/</URLBase>
  <device>
    <deviceType>urn:schemas-upnp-org:device:basic:1</deviceType>
    <friendlyName>Philips hue ($localIp)</friendlyName>
    <manufacturer>Royal Philips Electronics</manufacturer>
    <manufacturerURL>http://www.philips.com</manufacturerURL>
    <modelDescription>Philips hue Personal Wireless Lighting</modelDescription>
    <modelName>Philips hue bridge 2012</modelName>
    <modelNumber>929000226503</modelNumber>
    <modelURL>http://www.meethue.com</modelURL>
    <serialNumber>$serialNumber</serialNumber>
    <UDN>uuid:2f402f80-da50-11e1-9b23-$hueMac</UDN>
  </device>
</root>""".trimIndent()

            StreamLog.i(TAG, "Served Hue /description.xml")
            val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xml)
            resp.addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            return resp
        }

        private fun handleHueApi(session: IHTTPSession, uri: String): Response {
            val method = session.method
            val friendlyName = deviceNameProvider().ifBlank { "NoxMedia" }
            val isRunning = isRunningProvider()

            // 1. Initial Echo device registration (POST /api)
            if (method == Method.POST && (uri == "/api" || uri == "/api/")) {
                StreamLog.i(TAG, "Alexa Hue registered paired devicetype")
                val json = """[{"success":{"username":"1028d66426293e1073e523f45a64ac47"}}]"""
                val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                resp.addHeader("Access-Control-Allow-Origin", "*")
                return resp
            }

            // 2. Control light state (PUT /api/<user>/lights/1/state)
            if (method == Method.PUT && uri.contains("/lights/1/state")) {
                val postBody = try {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    map["postData"] ?: ""
                } catch (e: Exception) {
                    ""
                }

                val targetOn = postBody.contains("\"on\":true", ignoreCase = true) ||
                        postBody.contains("\"on\": true", ignoreCase = true)

                StreamLog.i(TAG, "Alexa Hue state command: TURN ${if (targetOn) "ON" else "OFF"}")
                onStateChange(targetOn)

                val json = """[{"success":{"/lights/1/state/on":$targetOn}}]"""
                val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                resp.addHeader("Access-Control-Allow-Origin", "*")
                return resp
            }

            // 3. List lights or single light details (GET /api/<user>/lights)
            val jsonLights = """{
  "1": {
    "state": {
      "on": $isRunning,
      "bri": 254,
      "alert": "none",
      "mode": "homeautomation",
      "reachable": true
    },
    "type": "Dimmable light",
    "name": "$friendlyName",
    "modelid": "LWB010",
    "manufacturername": "Philips",
    "productname": "Hue white lamp",
    "uniqueid": "00:17:88:01:00:bd:c7:de-0b",
    "swversion": "1.29.0_r21169"
  }
}""".trimIndent()

            val jsonFullConfig = """{
  "lights": $jsonLights,
  "config": {
    "name": "Philips hue",
    "swversion": "01036562",
    "mac": "$hueMac"
  }
}""".trimIndent()

            val responseBody = if (uri.endsWith("/lights") || uri.endsWith("/lights/1")) jsonLights else jsonFullConfig
            val resp = newFixedLengthResponse(Response.Status.OK, "application/json", responseBody)
            resp.addHeader("Access-Control-Allow-Origin", "*")
            return resp
        }
    }
}
