package com.hearing.hearingtest

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioEnhancer(context: Context) {

    private val interpreter: Interpreter

    init {
        val modelBytes = context.assets
            .open("audio_gain.tflite")
            .readBytes()

        val buffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())

        buffer.put(modelBytes)
        buffer.rewind()

        interpreter = Interpreter(buffer)
        Log.d("ML", "TFLite model loaded")
    }

    fun enhanceSamples(samples: FloatArray): FloatArray {

        val enhanced = FloatArray(samples.size)
        val frameSize = 1024

        val input = Array(1) { FloatArray(1) }
        val output = Array(1) { FloatArray(1) }

        var i = 0
        while (i < samples.size) {

            val end = minOf(i + frameSize, samples.size)
            var sum = 0.0

            for (j in i until end) {
                sum += samples[j] * samples[j]
            }

            val rms = sqrt(sum / (end - i)).toFloat()

            // 🔴 SAFETY for noisy audio
            if (!rms.isFinite()) {
                i += frameSize
                continue
            }

            // 🔹 REAL ML inference
            input[0][0] = rms
            interpreter.run(input, output)

            val gain = (1.0f + output[0][0])
                .coerceIn(0.5f, 1.5f)

            Log.d("ML", "RMS=$rms → ModelOut=${output[0][0]} → Gain=$gain")

            for (j in i until end) {
                enhanced[j] =
                    (samples[j] * gain).coerceIn(-1f, 1f)
            }

            i += frameSize
        }

        return enhanced
    }
}