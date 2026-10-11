package com.tgserver.streamer.server

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.posters.SpatialPosterService
import com.tgserver.streamer.telegram.TelegramClientManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalHttpServer(
    private val context: Context,
    private val config: ServerConfig,
    private val tgClient: TelegramClientManager,
    private val posterService: SpatialPosterService
) : NanoHTTPD("0.0.0.0", config.port) {

    private val gson = Gson()
    private val startTime = System.currentTimeMillis()

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val headers = session.headers

        // Handle CORS Preflight
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            addCorsHeaders(response)
            return response
        }

        val response = try {
            when {
                // 1. Stremio Addon Endpoints
                uri == "/manifest.json" -> handleManifest()
                uri.startsWith("/catalog/") -> handleCatalog(uri)
                uri.startsWith("/meta/") -> handleMeta(uri)
                uri.startsWith("/stream/") && !uri.startsWith("/stream/video/") -> handleStreamDescriptor(uri)

                // 2. HTTP 206 Partial Content Video Streaming Engine
                uri.startsWith("/stream/video/") -> handleVideoStream(uri, headers)

                // 3. Management REST APIs for Stitch UI
                uri == "/api/status" -> handleGetStatus()
                uri == "/api/server/toggle" && method == Method.POST -> handleToggleServer()
                uri == "/api/auth/status" -> handleGetAuthStatus()
                uri == "/api/auth/session" && method == Method.POST -> handleImportSession(session)
                uri == "/api/auth/send-code" && method == Method.POST -> handleSendCode(session)
                uri == "/api/auth/sign-in" && method == Method.POST -> handleSignIn(session)
                uri == "/api/auth/password" && method == Method.POST -> handleVerify2FA(session)
                uri == "/api/auth/logout" && method == Method.POST -> handleLogout()
                uri == "/api/channels" -> handleGetChannels()
                uri == "/api/channels/add" && method == Method.POST -> handleAddChannel(session)
                uri == "/api/channels/toggle" && method == Method.POST -> handleToggleChannel(session)
                uri.startsWith("/api/channels/") && method == Method.DELETE -> handleDeleteChannel(uri)
                uri.startsWith("/api/library") -> handleGetLibrary(session)
                uri == "/api/settings" && method == Method.GET -> handleGetSettings()
                uri == "/api/settings" && method == Method.POST -> handleSaveSettings(session)

                // 4. Web UI Static Files
                else -> serveStaticAsset(uri)
            }
        } catch (e: Exception) {
            val err = JsonObject().apply { addProperty("error", e.message) }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", gson.toJson(err))
        }

        addCorsHeaders(response)
        return response
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Max-Age", "86400")
    }

    // --- Stremio Addon Handlers ---

    private fun handleManifest(): Response {
        val manifest = JsonObject().apply {
            addProperty("id", "org.tgserver.streamer")
            addProperty("version", "1.0.0")
            addProperty("name", "TG-Server Streamer")
            addProperty("description", "Direct Telegram channel video streamer with SpatialPosters integration")
            addProperty("logo", "https://lh3.googleusercontent.com/aida/AEtjO1XxF5nLod1dKj8IrkfY2sKq1asKsR74gub1jDyICCeoafQ6Dh5N6bGXc8PAqlJkXYlWXajcnx4pVNnZX2pM9Ra923Ml5jb0jvAdarsZqKIj-yYVPDpCufiBbr8kb-kHeO3ymMD4-VoMVeVlJYOx8J0b4lEJpK2yAgUA8edYgOHhCTWrPUrSYWP6ec9qa80nJgzFgxMxgeIMqJ4lOX40yq670kg84dITxJghMvwrPMzL_NtP5hBpi82AQyAN")
            add("types", gson.toJsonTree(listOf("movie", "series")))
            add("catalogs", gson.toJsonTree(listOf(
                mapOf("type" to "movie", "id" to "tg_movies", "name" to "Telegram Movies"),
                mapOf("type" to "series", "id" to "tg_series", "name" to "Telegram Series")
            )))
            add("resources", gson.toJsonTree(listOf("catalog", "stream", "meta")))
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(manifest))
    }

    private fun handleCatalog(uri: String): Response {
        val isSeries = uri.contains("/series/")
        val mediaList = tgClient.getAllMedia().filter {
            if (isSeries) it.type == "series" else it.type == "movie"
        }

        val metasArray = mediaList.map { item ->
            val poster = item.posterUrl ?: posterService.resolvePoster(
                item.title,
                config.customPosterUrl,
                config.customPosterApiKey
            )
            item.posterUrl = poster

            mapOf(
                "id" to item.id,
                "type" to item.type,
                "name" to item.title,
                "poster" to (poster ?: "https://images.metahub.space/poster/medium/placeholder/img"),
                "releaseInfo" to (item.year ?: "2024"),
                "description" to (item.overview ?: "${item.quality} • ${item.formattedSize}")
            )
        }

        val res = mapOf("metas" to metasArray)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleMeta(uri: String): Response {
        val parts = uri.removeSuffix(".json").split("/")
        val id = parts.lastOrNull() ?: ""
        val item = tgClient.getMediaById(id)

        if (item == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"meta\":null}")
        }

        val poster = item.posterUrl ?: posterService.resolvePoster(
            item.title,
            config.customPosterUrl,
            config.customPosterApiKey
        )

        val metaObj = mapOf(
            "id" to item.id,
            "type" to item.type,
            "name" to item.title,
            "year" to (item.year ?: "2024"),
            "poster" to (poster ?: "https://images.metahub.space/poster/medium/placeholder/img"),
            "description" to (item.overview ?: "${item.quality} • ${item.formattedSize} • ${item.formattedDuration}"),
            "genres" to listOf("Telegram", item.quality)
        )

        val res = mapOf("meta" to metaObj)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleStreamDescriptor(uri: String): Response {
        val parts = uri.removeSuffix(".json").split("/")
        val id = parts.lastOrNull() ?: ""
        val item = tgClient.getMediaById(id)

        if (item == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"streams\":[]}")
        }

        val localIp = getLocalIpAddress()
        val streamUrl = "http://$localIp:${config.port}/stream/video/${item.channelId}/${item.messageId}"

        val stream = mapOf(
            "name" to "TG-Server (${item.quality})",
            "title" to "${item.title} [${item.quality} • ${item.formattedSize}]",
            "url" to streamUrl
        )

        val res = mapOf("streams" to listOf(stream))
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    // --- Video Streaming Engine (HTTP 206 Partial Content) ---

    private fun handleVideoStream(uri: String, headers: Map<String, String>): Response {
        val parts = uri.split("/")
        // uri format: /stream/video/<channelId>/<messageId>
        if (parts.size < 5) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid stream path")
        }

        val channelId = parts[3].toLongOrNull() ?: 0L
        val messageId = parts[4].toIntOrNull() ?: 0

        val rangeHeader = headers["range"]
        var startByte = 0L
        var endByte = -1L

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIndex = rangeVal.indexOf('-')
            if (dashIndex != -1) {
                val startStr = rangeVal.substring(0, dashIndex)
                val endStr = rangeVal.substring(dashIndex + 1)
                startByte = startStr.toLongOrNull() ?: 0L
                endByte = endStr.toLongOrNull() ?: -1L
            }
        }

        val chunk = tgClient.getStreamSource(channelId, messageId, startByte, endByte)

        val response = if (rangeHeader != null) {
            newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                chunk.mimeType,
                chunk.inputStream,
                chunk.contentLength
            ).apply {
                addHeader("Content-Range", "bytes ${chunk.startByte}-${chunk.endByte}/${chunk.totalSize}")
            }
        } else {
            newFixedLengthResponse(
                Response.Status.OK,
                chunk.mimeType,
                chunk.inputStream,
                chunk.totalSize
            )
        }

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", chunk.contentLength.toString())
        return response
    }

    // --- REST APIs for Stitch Web UI ---

    private fun handleGetStatus(): Response {
        val localIp = getLocalIpAddress()
        val uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        val obj = JsonObject().apply {
            addProperty("isRunning", config.isRunning)
            addProperty("port", config.port)
            addProperty("localIp", localIp)
            addProperty("manifestUrl", "http://$localIp:${config.port}/manifest.json")
            addProperty("uptimeSeconds", uptimeSeconds)
            addProperty("uptimeFormatted", formatUptime(uptimeSeconds))
            addProperty("memoryUsedMb", usedMem)
            addProperty("memoryTotalMb", maxMem)
            addProperty("cpuPercent", 4.2)
            addProperty("totalChannels", tgClient.getChannels().size)
            addProperty("totalMedia", tgClient.getAllMedia().size)
            addProperty("activeStreams", 1)
            add("user", gson.toJsonTree(mapOf(
                "username" to tgClient.sessionState.value.username,
                "firstName" to tgClient.sessionState.value.firstName,
                "isLoggedIn" to tgClient.sessionState.value.isLoggedIn
            )))
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(obj))
    }

    private fun formatUptime(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return if (d > 0) "${d}d ${h}h" else "${h}h ${m}m"
    }

    private fun handleToggleServer(): Response {
        config.isRunning = !config.isRunning
        return handleGetStatus()
    }

    private fun handleGetAuthStatus(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(tgClient.sessionState.value))
    }

    private fun handleSendCode(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val phone = body?.get("phoneNumber")?.asString ?: ""
        val result = tgClient.sendLoginCode(phone)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.addProperty("message", result.getOrNull())
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleSignIn(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val code = body?.get("code")?.asString ?: ""
        val result = tgClient.verifyLoginCode(code)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleVerify2FA(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val password = body?.get("password")?.asString ?: ""
        val result = tgClient.verify2FAPassword(password)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleImportSession(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val sessionString = body?.get("sessionString")?.asString ?: ""
        val result = tgClient.importSessionString(sessionString)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleLogout(): Response {
        tgClient.logout()
        return handleGetAuthStatus()
    }

    private fun handleGetChannels(): Response {
        val list = tgClient.getChannels()
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleAddChannel(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val identifier = body?.get("identifier")?.asString ?: ""
        val channel = tgClient.addChannel(identifier)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(channel))
    }

    private fun handleToggleChannel(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val id = body?.get("id")?.asLong ?: 0L
        val active = body?.get("active")?.asBoolean ?: true
        tgClient.toggleChannel(id, active)
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
    }

    private fun handleDeleteChannel(uri: String): Response {
        val id = uri.substringAfterLast("/").toLongOrNull() ?: 0L
        tgClient.removeChannel(id)
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
    }

    private fun handleGetLibrary(session: IHTTPSession): Response {
        val params = session.parameters
        val query = params["q"]?.firstOrNull() ?: ""
        val category = params["category"]?.firstOrNull() ?: "all"
        val media = tgClient.searchMedia(query, category)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(media))
    }

    private fun handleGetSettings(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(config))
    }

    private fun handleSaveSettings(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        body?.let {
            if (it.has("port")) config.port = it.get("port").asInt
            if (it.has("customPosterUrl")) config.customPosterUrl = it.get("customPosterUrl").asString
            if (it.has("customPosterApiKey")) config.customPosterApiKey = it.get("customPosterApiKey").asString
            if (it.has("bufferChunkSizeKb")) config.bufferChunkSizeKb = it.get("bufferChunkSizeKb").asInt
        }
        return handleGetSettings()
    }

    private fun getRequestBody(session: IHTTPSession): JsonObject? {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val postData = map["postData"]
        return if (!postData.isNullOrBlank()) {
            JsonParser.parseString(postData).asJsonObject
        } else {
            null
        }
    }

    private fun serveStaticAsset(uri: String): Response {
        val info = JsonObject().apply {
            addProperty("name", "TG-Server Mobile Streamer")
            addProperty("version", "1.0.0")
            addProperty("status", if (config.isRunning) "online" else "paused")
            addProperty("port", config.port)
            addProperty("manifest", "http://${getLocalIpAddress()}:${config.port}/manifest.json")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(info))
    }
}
