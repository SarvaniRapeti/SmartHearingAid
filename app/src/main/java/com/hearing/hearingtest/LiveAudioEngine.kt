package com.hearing.hearingtest

import android.content.Context
import android.media.*
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.*
import kotlin.math.*

class LiveAudioEngine {

    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var job: Job? = null

    @Volatile
    private var running = false

    lateinit var context: Context

    var onStopped: (() -> Unit)? = null

    // 🔹 LIVE DSP STATE
    private val frameSize = 256
    private val noiseMag = FloatArray(frameSize)

    fun start(
        scope: CoroutineScope,
        waveform: SnapshotStateList<Float>,
        noiseCancellationEnabled: Boolean
    ) {
        if (running) return
        running = true

        job = scope.launch(Dispatchers.Default) {

            val sampleRate = 48000

            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufBytes <= 0) {
                autoStop()
                return@launch
            }

            val bufferBytes = minBufBytes * 2

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )

            val audioTrack =
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

            record = audioRecord
            track = audioTrack

            val input = ShortArray(frameSize)
            val output = ShortArray(frameSize)
            var lastUiUpdate = 0L

            try {
                audioRecord.startRecording()
                audioTrack.play()

                while (isActive && running) {

                    val read = audioRecord.read(input, 0, frameSize)
                    if (read <= 0) break

                    if (noiseCancellationEnabled) {
                        processLiveFrame(input, output, read, sampleRate)
                        audioTrack.write(output, 0, read)
                    } else {
                        audioTrack.write(input, 0, read)
                    }

                    // 🔹 RMS waveform
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

            } finally {
                cleanupInternal()
            }
        }
    }

    // =========================================================
    // LIGHTWEIGHT REAL-TIME SPECTRAL SUBTRACTION
    // =========================================================
    private fun processLiveFrame(
        input: ShortArray,
        output: ShortArray,
        length: Int,
        sampleRate: Int
    ) {
        val real = FloatArray(frameSize)
        val imag = FloatArray(frameSize)

        for (i in 0 until frameSize) {
            real[i] = if (i < length) input[i] / 32768f else 0f
        }

        fft(real, imag)

        for (i in 0 until frameSize) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            noiseMag[i] = 0.98f * noiseMag[i] + 0.02f * mag

            val cleanMag = max(mag - noiseMag[i], noiseMag[i] * 0.1f)
            val phase = atan2(imag[i], real[i])

            real[i] = cleanMag * cos(phase)
            imag[i] = cleanMag * sin(phase)
        }

        ifft(real, imag)

        for (i in 0 until length) {
            output[i] = (real[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
    }

    fun stop() {
        running = false
        job?.cancel()
    }

    private fun autoStop() {
        running = false
        job?.cancel()
        CoroutineScope(Dispatchers.Main).launch {
            onStopped?.invoke()
        }
    }

    private fun cleanupInternal() {
        try { record?.stop() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        record = null
        track = null
        job = null
    }

    // =========================================================
    // FFT HELPERS (reuse logic)
    // =========================================================
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        val levels = log2(n.toDouble()).toInt()

        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - levels)
            if (j > i) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }

        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            for (i in 0 until n step size) {
                for (j in 0 until half) {
                    val k = j * step
                    val angle = -2 * Math.PI * k / n
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()

                    val tr = wr * real[i + j + half] - wi * imag[i + j + half]
                    val ti = wr * imag[i + j + half] + wi * real[i + j + half]

                    real[i + j + half] = real[i + j] - tr
                    imag[i + j + half] = imag[i + j] - ti

                    real[i + j] += tr
                    imag[i + j] += ti
                }
            }
            size *= 2
        }
    }

    private fun ifft(real: FloatArray, imag: FloatArray) {
        for (i in real.indices) imag[i] = -imag[i]
        fft(real, imag)
        val n = real.size.toFloat()
        for (i in real.indices) {
            real[i] /= n
            imag[i] = -imag[i] / n
        }
    }
}
