package com.hearing.hearingtest

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object WavUtils {

    data class WavData(
        val samples: FloatArray,
        val sampleRate: Int
    )

    // =========================================================
    // READ WAV (16-bit PCM, mono or stereo → mono FloatArray)
    // =========================================================
    fun readWav(file: File): WavData {

        val raf = RandomAccessFile(file, "r")

        // 🔹 Channels
        raf.seek(22)
        val channels =
            java.lang.Short.reverseBytes(raf.readShort()).toInt()

        // 🔹 Sample rate
        raf.seek(24)
        val sampleRate =
            Integer.reverseBytes(raf.readInt())

        // 🔹 Bits per sample
        raf.seek(34)
        val bits =
            java.lang.Short.reverseBytes(raf.readShort()).toInt()
        require(bits == 16) { "Only 16-bit PCM WAV supported" }

        // 🔹 Find DATA chunk
        raf.seek(12)
        var dataSize = 0
        while (true) {
            val id = ByteArray(4)
            raf.readFully(id)
            val size =
                Integer.reverseBytes(raf.readInt())
            if (String(id) == "data") {
                dataSize = size
                break
            } else {
                raf.seek(raf.filePointer + size)
            }
        }

        // 🔹 Total frames (NOT raw samples)
        val totalFrames = dataSize / (2 * channels)
        val samples = FloatArray(totalFrames)

        // 🔹 Read samples (downmix stereo → mono)
        for (i in 0 until totalFrames) {
            if (channels == 1) {
                samples[i] =
                    java.lang.Short.reverseBytes(raf.readShort()) / 32768f
            } else {
                val left =
                    java.lang.Short.reverseBytes(raf.readShort()).toInt()
                val right =
                    java.lang.Short.reverseBytes(raf.readShort()).toInt()
                samples[i] =
                    ((left + right) / 2) / 32768f
            }
        }

        raf.close()
        return WavData(samples, sampleRate)
    }

    // =========================================================
    // WRITE WAV (mono, 16-bit PCM)
    // =========================================================
    fun writeWav(
        file: File,
        samples: FloatArray,
        sampleRate: Int
    ) {

        val raf = RandomAccessFile(file, "rw")
        raf.setLength(0)

        val channels = 1
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8

        val dataSize = samples.size * bytesPerSample
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample

        fun writeString(s: String) {
            raf.writeBytes(s)
        }

        fun writeIntLE(v: Int) {
            raf.write(
                ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(v)
                    .array()
            )
        }

        fun writeShortLE(v: Short) {
            raf.write(
                ByteBuffer.allocate(2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(v)
                    .array()
            )
        }

        // 🔹 RIFF header
        writeString("RIFF")
        writeIntLE(36 + dataSize)
        writeString("WAVE")

        // 🔹 fmt chunk
        writeString("fmt ")
        writeIntLE(16)                       // PCM
        writeShortLE(1)                      // Audio format = PCM
        writeShortLE(channels.toShort())     // MONO
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(blockAlign.toShort())
        writeShortLE(bitsPerSample.toShort())

        // 🔹 data chunk
        writeString("data")
        writeIntLE(dataSize)

        // 🔹 Write samples
        for (s in samples) {
            val v = max(-1f, min(1f, s))
            writeShortLE((v * 32767).toInt().toShort())
        }

        raf.close()
    }
}
