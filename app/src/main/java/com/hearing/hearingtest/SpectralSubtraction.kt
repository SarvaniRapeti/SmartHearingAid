package com.hearing.hearingtest

import kotlin.math.*

object SpectralSubtraction {

    fun enhance(
        samples: FloatArray,
        sampleRate: Int
    ): FloatArray {

        val frameSize = 512
        val hop = frameSize / 2
        val output = FloatArray(samples.size)

        // =========================================================
        // Noise estimation (first 0.5 seconds)
        // =========================================================
        val noiseFrames = max(1, (sampleRate * 0.5 / hop).toInt())
        val noiseMag = FloatArray(frameSize)

        for (f in 0 until noiseFrames) {
            val real = FloatArray(frameSize)
            val imag = FloatArray(frameSize)

            for (i in 0 until frameSize) {
                val idx = f * hop + i
                if (idx < samples.size) real[i] = samples[idx]
            }

            fft(real, imag)

            for (i in 0 until frameSize) {
                val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
                noiseMag[i] = noiseMag[i] + mag
            }
        }

        for (i in noiseMag.indices) {
            noiseMag[i] = noiseMag[i] / noiseFrames
        }

        // =========================================================
        // Spectral subtraction + speech enhancement
        // =========================================================
        var pos = 0
        while (pos + frameSize < samples.size) {

            val real = FloatArray(frameSize)
            val imag = FloatArray(frameSize)

            for (i in 0 until frameSize) {
                real[i] = samples[pos + i]
            }

            fft(real, imag)

            for (i in 0 until frameSize) {
                val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
                val phase = atan2(imag[i], real[i])

                // --- Noise reduction with spectral floor ---
                val cleanMag = max(
                    mag - noiseMag[i] * 1.5f,
                    noiseMag[i] * 0.1f
                )

                val freq = i * sampleRate.toFloat() / frameSize
                var enhancedMag = cleanMag

                // --- Speech band emphasis (gentle) ---
                if (freq in 300f..3400f) {
                    enhancedMag *= 1.1f
                }

                // --- Low-frequency noise suppression ---
                if (freq < 200f) {
                    enhancedMag *= 0.3f
                }

                real[i] = enhancedMag * cos(phase)
                imag[i] = enhancedMag * sin(phase)
            }

            ifft(real, imag)

            for (i in 0 until frameSize) {
                output[pos + i] = output[pos + i] + real[i]
            }

            pos += hop
        }

        // =========================================================
        // FINAL STEP: Gentle speech-safe compression
        // =========================================================
        return compress(output)
    }

    // =========================================================
    // Dynamic Range Compression (tuned, noise-safe)
    // =========================================================
    private fun compress(samples: FloatArray): FloatArray {
        val out = FloatArray(samples.size)

        val threshold = 0.08f     // do not boost quiet noise
        val ratio = 2.0f          // gentle compression
        val makeupGain = 1.2f     // mild loudness boost

        for (i in samples.indices) {
            val x = samples[i]
            val abs = abs(x)

            // --- Noise gate for very quiet regions ---
            if (abs < 0.02f) {
                out[i] = 0f
                continue
            }

            val y =
                if (abs < threshold) {
                    x
                } else {
                    sign(x) * (threshold + (abs - threshold) / ratio)
                }

            out[i] = y * makeupGain
        }

        return out
    }

    // =========================================================
    // FFT IMPLEMENTATION
    // =========================================================
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        val levels = log2(n.toDouble()).toInt()

        for (i in 0 until n) {
            val j = reverseBits(i, levels)
            if (j > i) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr

                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
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

                    val tr =
                        wr * real[i + j + half] -
                                wi * imag[i + j + half]

                    val ti =
                        wr * imag[i + j + half] +
                                wi * real[i + j + half]

                    real[i + j + half] = real[i + j] - tr
                    imag[i + j + half] = imag[i + j] - ti

                    real[i + j] = real[i + j] + tr
                    imag[i + j] = imag[i + j] + ti
                }
            }
            size *= 2
        }
    }

    private fun ifft(real: FloatArray, imag: FloatArray) {
        for (i in real.indices) {
            imag[i] = -imag[i]
        }

        fft(real, imag)

        val n = real.size.toFloat()
        for (i in real.indices) {
            real[i] = real[i] / n
            imag[i] = -imag[i] / n
        }
    }

    private fun reverseBits(x: Int, bits: Int): Int {
        var v = x
        var r = 0
        repeat(bits) {
            r = (r shl 1) or (v and 1)
            v = v shr 1
        }
        return r
    }
}
