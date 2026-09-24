package org.futo.inputmethod.latin.fork.whisper.remote

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV (RIFF / PCM16 mono) encoder.
 *
 * The voice input pipeline records mono 16 kHz audio and hands inference a [FloatArray] normalized
 * to [-1.0, 1.0] (see AudioRecognizer). OpenAI-compatible transcription endpoints expect an audio
 * file upload, so we serialize that float buffer to a WAV byte array here.
 */
object WavEncoder {
    private const val HEADER_SIZE = 44

    /**
     * Encode [samples] (normalized floats in [-1, 1]) as a 16-bit PCM mono WAV file.
     */
    fun floatArrayToWavBytes(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * numChannels * bytesPerSample
        val blockAlign = numChannels * bytesPerSample
        val dataSize = samples.size * bytesPerSample

        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize) // ChunkSize = 36 + SubChunk2Size
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat = 1 (PCM)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // "data" sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val intSample = (clamped * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer.putShort(intSample.toShort())
        }

        return buffer.array()
    }

    /**
     * A short silent WAV clip, used to validate a remote endpoint's transcription path without
     * sending real audio.
     */
    fun silentWavBytes(durationSeconds: Float = 0.2f, sampleRate: Int = 16000): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(floatArrayToWavBytes(FloatArray((durationSeconds * sampleRate).toInt()), sampleRate))
        return out.toByteArray()
    }
}
