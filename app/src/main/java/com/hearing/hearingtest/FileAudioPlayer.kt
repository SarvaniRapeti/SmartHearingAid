package com.hearing.hearingtest

import android.media.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileAudioPlayer {

    private var track: AudioTrack? = null
    private var job: Job? = null

    fun play(
        file: File,
        scope: CoroutineScope,
        onFinished: () -> Unit = {}
    ) {
        stop()

        job = scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 44100

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val audioTrack =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setSampleRate(sampleRate)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setBufferSizeInBytes(bufferSize)
                            .build()
                    } else {
                        AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                        )
                    }

                track = audioTrack
                audioTrack.play()

                val fis = FileInputStream(file)
                val byteBuffer = ByteArray(bufferSize)
                val shortBuffer = ShortArray(bufferSize / 2)

                while (isActive) {
                    val bytesRead = fis.read(byteBuffer)
                    if (bytesRead <= 0) break

                    ByteBuffer.wrap(byteBuffer, 0, bytesRead)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shortBuffer, 0, bytesRead / 2)

                    audioTrack.write(shortBuffer, 0, bytesRead / 2)
                }

                fis.close()
            } finally {
                stop()
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}

        track = null
    }
}
