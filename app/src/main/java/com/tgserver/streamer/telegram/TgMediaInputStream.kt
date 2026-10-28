package com.tgserver.streamer.telegram

import com.chaquo.python.PyObject
import com.tgserver.streamer.server.StreamLog
import com.tgserver.streamer.server.StreamTrafficStats
import java.io.InputStream

private const val TAG = "TgMediaInputStream"

/**
 * An InputStream that streams bytes directly from Telegram MTProto via Chaquopy Python bridge.
 * Supports arbitrary byte ranges (HTTP 206 Partial Content) and transparently spans across
 * multi-part split files (.mkv.001, .mkv.002, etc.).
 */
class TgMediaInputStream(
    private val bridge: PyObject,
    private val streamId: String,
    private val totalBytesToRead: Long,
    private val chunkSize: Int = 2097152, // 2MB chunk buffer (doubled to minimize JNI calls and smooth streaming)
    private val onClosed: ((String) -> Unit)? = null
) : InputStream() {

    private val openTime = System.currentTimeMillis()
    private var totalBytesRead: Long = 0
    private var currentBuffer: ByteArray? = null
    private var bufferPos: Int = 0
    private var isClosed = false
    private var hasLoggedFirstChunk = false
    private var lastLoggedMb = 0L

    init {
        StreamLog.i(TAG, "Stream $streamId: opened with totalToRead=$totalBytesToRead bytes (${totalBytesToRead / (1024 * 1024)} MB)")
    }

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n <= 0) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isClosed || totalBytesRead >= totalBytesToRead) {
            return -1
        }

        if (len <= 0) return 0

        // If current buffer is exhausted, fetch next chunk from Python
        if (currentBuffer == null || bufferPos >= currentBuffer!!.size) {
            val bytesNeeded = minOf(chunkSize.toLong(), totalBytesToRead - totalBytesRead).toInt()
            if (bytesNeeded <= 0) return -1

            val pyBytes = try {
                bridge.callAttr("read_stream_chunk", streamId, bytesNeeded)
            } catch (e: Exception) {
                StreamLog.e(TAG, "Stream $streamId: Error fetching stream chunk: ${e.message}", e)
                null
            }

            if (pyBytes == null) {
                StreamLog.w(TAG, "Stream $streamId: Python returned null chunk at totalRead=$totalBytesRead/$totalBytesToRead")
                return -1
            }

            val nextChunk = pyBytes.toJava(ByteArray::class.java)
            if (nextChunk.isEmpty()) {
                val elapsed = System.currentTimeMillis() - openTime
                StreamLog.i(TAG, "Stream $streamId: EOF reached at totalRead=$totalBytesRead/$totalBytesToRead bytes in ${elapsed}ms")
                return -1
            }

            if (!hasLoggedFirstChunk) {
                hasLoggedFirstChunk = true
                val elapsed = System.currentTimeMillis() - openTime
                StreamLog.i(TAG, "Stream $streamId: First chunk received (${nextChunk.size} bytes) in ${elapsed}ms")
            }

            currentBuffer = nextChunk
            bufferPos = 0
        }

        val buf = currentBuffer!!
        val available = buf.size - bufferPos
        val remainingTotal = totalBytesToRead - totalBytesRead
        val toCopy = minOf(minOf(len.toLong(), available.toLong()), remainingTotal).toInt()

        if (toCopy <= 0) return -1

        System.arraycopy(buf, bufferPos, b, off, toCopy)
        bufferPos += toCopy
        totalBytesRead += toCopy
        StreamTrafficStats.recordBytes(toCopy.toLong())

        val currentMb = totalBytesRead / (10 * 1024 * 1024L)
        if (currentMb > lastLoggedMb) {
            lastLoggedMb = currentMb
            val totalMb = totalBytesToRead / (1024 * 1024L)
            val readMb = totalBytesRead / (1024 * 1024L)
            val pct = if (totalBytesToRead > 0) (totalBytesRead * 100 / totalBytesToRead) else 0
            StreamLog.i(TAG, "Stream $streamId progress: $readMb MB / $totalMb MB ($pct%) delivered to player")
        }

        return toCopy
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            val elapsed = System.currentTimeMillis() - openTime
            val readMb = totalBytesRead / (1024 * 1024L)
            val totalMb = totalBytesToRead / (1024 * 1024L)
            val pct = if (totalBytesToRead > 0) (totalBytesRead * 100 / totalBytesToRead) else 0
            StreamLog.i(TAG, "Stream $streamId: close() called after $readMb MB / $totalMb MB ($pct%) in ${elapsed}ms")
            try {
                bridge.callAttr("close_stream", streamId)
            } catch (e: Exception) {
                StreamLog.w(TAG, "Error closing stream $streamId: ${e.message}")
            }
            try {
                onClosed?.invoke(streamId)
            } catch (e: Exception) {
                StreamLog.w(TAG, "Error in onClosed callback for stream $streamId: ${e.message}")
            }
        }
        super.close()
    }
}
