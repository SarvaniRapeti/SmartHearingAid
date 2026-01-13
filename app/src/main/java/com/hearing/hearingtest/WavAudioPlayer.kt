package com.hearing.hearingtest

import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

class WavAudioPlayer(private val context: Context) {

    var onDurationReady: ((Int) -> Unit)? = null
    var onPositionUpdate: ((Int) -> Unit)? = null
    var onCompleted: (() -> Unit)? = null

    private var track: AudioTrack? = null
    private var job: Job? = null

    private var paused = false

    private var currentSample = 0
    private var totalSamples = 0
    private var sampleRate = 44100

    var isPlaying = false
        private set

    fun play(file: File, scope: CoroutineScope) {
        stop()

        job = scope.launch(Dispatchers.Default) {
            try {
                val wav = RandomAccessFile(file, "r")

                // ---- WAV HEADER (LITTLE ENDIAN) ----
                wav.seek(22)
                val channels =
                    java.lang.Short.reverseBytes(wav.readShort()).toInt()

                wav.seek(24)
                sampleRate =
                    Integer.reverseBytes(wav.readInt())

                wav.seek(34)
                val bits =
                    java.lang.Short.reverseBytes(wav.readShort()).toInt()

                Log.d("WAV", "channels=$channels sampleRate=$sampleRate bits=$bits")

                if (bits != 16) {
                    Log.e("WAV", "Only 16-bit PCM supported")
                    wav.close()
                    return@launch
                }

                // ---- FIND DATA CHUNK ----
                wav.seek(12)
                var dataSize = 0
                while (true) {
                    val chunkId = ByteArray(4)
                    wav.readFully(chunkId)
                    val chunkSize =
                        Integer.reverseBytes(wav.readInt())

                    if (String(chunkId) == "data") {
                        dataSize = chunkSize
                        break
                    } else {
                        wav.seek(wav.filePointer + chunkSize)
                    }
                }

                totalSamples = dataSize / (2 * channels)
                val durationMs = (totalSamples * 1000) / sampleRate
                onDurationReady?.invoke(durationMs)

                val channelOut = AudioFormat.CHANNEL_OUT_MONO


                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelOut,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelOut,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )

                track = audioTrack
                currentSample = 0
                isPlaying = true

                audioTrack.play()

                val buffer = ShortArray(bufferSize / 2)

                while (isActive && currentSample < totalSamples) {

                    if (paused) {
                        delay(20)
                        continue
                    }

                    val toRead =
                        min(buffer.size, totalSamples - currentSample)

                    for (i in 0 until toRead) {
                        if (channels == 1) {
                            buffer[i] = java.lang.Short.reverseBytes(wav.readShort())
                        } else {
                            val left = java.lang.Short.reverseBytes(wav.readShort()).toInt()
                            val right = java.lang.Short.reverseBytes(wav.readShort()).toInt()
                            buffer[i] = ((left + right) / 2).toShort()
                        }
                    }

                    audioTrack.write(buffer, 0, toRead)
                    currentSample += toRead

                    val posMs = (currentSample * 1000) / sampleRate
                    onPositionUpdate?.invoke(posMs)
                }

                wav.close()

// 🔹 Only treat as completed if NOT paused
                if (!paused) {
                    stop()
                    onCompleted?.invoke()
                }


            } catch (e: Exception) {
                Log.e("WAV", "Playback error", e)
                stop()
            }
        }
    }

    fun pause() {
        if (!isPlaying) return
        paused = true
        isPlaying = false
        track?.pause()
    }

    fun resume() {
        if (!paused) return
        paused = false
        isPlaying = true
        track?.play()
    }

    fun stop() {
        isPlaying = false
        paused = false
        job?.cancel()
        job = null

        try { track?.pause() } catch (_: Exception) {}
        try { track?.flush() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}

        track = null
        currentSample = 0
    }
}
