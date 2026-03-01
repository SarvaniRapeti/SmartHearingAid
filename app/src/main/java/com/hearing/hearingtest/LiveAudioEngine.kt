package com.hearing.hearingtest

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.*
import kotlin.math.sqrt

class LiveAudioEngine {

    companion object {
        private const val TAG = "LiveAudioEngine"
    }

    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var job: Job? = null

    @Volatile
    private var noiseCancellationOn = false

    @Volatile
    private var running = false

    lateinit var context: Context

    var onStopped: (() -> Unit)? = null



    private val floatFrame = FloatArray(frameSize)
    private var floatIndex = 0




    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        waveform: SnapshotStateList<Float>,
        noiseCancellationEnabled: Boolean
    ) {
        if (running) return
        running = true

        noiseCancellationOn = noiseCancellationEnabled

        Log.d(TAG, "Start listening pressed")



    

        job = scope.launch(Dispatchers.Default) {

            Log.d(TAG, "Audio coroutine started")

            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufBytes <= 0) {
                Log.e(TAG, "Invalid min buffer size")
                autoStop()
                return@launch
            }

            val bufferBytes = minBufBytes * 2

            val audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord creation failed", e)
                autoStop()
                return@launch
            }

            val audioTrack = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build()
                } else {
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes,
                        AudioTrack.MODE_STREAM
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack creation failed", e)
                autoStop()
                return@launch
            }

            record = audioRecord
            track = audioTrack

            val input = ShortArray(frameSize)
            val output = ShortArray(frameSize)
            var lastUiUpdate = 0L

            try {
                audioRecord.startRecording()
                audioTrack.play()
                Log.d(TAG, "Audio started")

                while (isActive && running) {

                    val read = audioRecord.read(input, 0, input.size)
                    if (read <= 0) break

                    for (i in 0 until read) {

                        floatFrame[floatIndex++] = input[i] / 32768f

                        if (floatIndex == frameSize) {


                            for (j in 0 until frameSize) {
                                output[j] =
                                    (floatFrame[j].coerceIn(-1f, 1f) * 32767)
                                        .toInt()
                                        .toShort()
                            }

                            audioTrack.write(output, 0, frameSize)
                            floatIndex = 0
                        }
                    }

                    // RMS waveform
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = input[i].toDouble()
                        sum += v * v
                    }

                    val rms = sqrt(sum / read) / Short.MAX_VALUE
                    val now = System.currentTimeMillis()

                    if (now - lastUiUpdate >= 50) {
                        lastUiUpdate = now
                        withContext(Dispatchers.Main) {
                            if (waveform.size >= 120) waveform.removeAt(0)
                            waveform.add(rms.toFloat())
                        }
                    }
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Live audio loop crashed", e)
            } finally {
                cleanupInternal()
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stop called")
        running = false
        job?.cancel()
    }

    private fun autoStop() {
        Log.d(TAG, "Auto stop triggered")
        running = false
        job?.cancel()
        CoroutineScope(Dispatchers.Main).launch {
            onStopped?.invoke()
        }
    }

    private fun cleanupInternal() {
        Log.d(TAG, "Cleaning up audio")
        try { record?.stop() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        record = null
        track = null
        job = null
        floatIndex = 0
    }

    fun setNoiseCancellationEnabled(enabled: Boolean) {
        noiseCancellationOn = enabled
        Log.d(TAG, "Noise cancellation toggle = $enabled")
    }

}
