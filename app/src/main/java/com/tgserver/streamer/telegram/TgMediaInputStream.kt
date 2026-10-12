package com.tgserver.streamer.telegram

import android.util.Log
import com.chaquo.python.PyObject
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
    private val chunkSize: Int = 262144
) : InputStream() {

    private var totalBytesRead: Long = 0
    private var currentBuffer: ByteArray? = null
    private var bufferPos: Int = 0
    private var isClosed = false

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
                Log.e(TAG, "Error fetching stream chunk: ${e.message}", e)
                null
            }

            if (pyBytes == null) {
                return -1
            }

            val nextChunk = pyBytes.toJava(ByteArray::class.java)
            if (nextChunk.isEmpty()) {
                return -1
            }

            currentBuffer = nextChunk
            bufferPos = 0
        }

        val buf = currentBuffer!!
        val available = buf.size - bufferPos
        val remainingTotal = (totalBytesToRead - totalBytesRead).toInt()
        val toCopy = minOf(len, available, remainingTotal)

        if (toCopy <= 0) return -1

        System.arraycopy(buf, bufferPos, b, off, toCopy)
        bufferPos += toCopy
        totalBytesRead += toCopy

        return toCopy
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            try {
                bridge.callAttr("close_stream", streamId)
            } catch (e: Exception) {
                Log.w(TAG, "Error closing stream $streamId: ${e.message}")
            }
        }
        super.close()
    }
}
