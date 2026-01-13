package com.hearing.hearingtest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SimpleDsp {

    /**
     * Lightweight speech-oriented DSP
     * - DC removal
     * - Noise gate
     * - High-frequency emphasis
     */
    fun process(samples: FloatArray): FloatArray {

        val out = FloatArray(samples.size)

        // ---------- 1️⃣ DC REMOVAL ----------
        var dc = 0f
        val dcAlpha = 0.995f

        for (i in samples.indices) {
            dc = dcAlpha * dc + (1 - dcAlpha) * samples[i]
            out[i] = samples[i] - dc
        }

        // ---------- 2️⃣ NOISE GATE ----------
        val noiseFloor = 0.015f  // adjust if needed

        for (i in out.indices) {
            if (abs(out[i]) < noiseFloor) {
                out[i] *= 0.3f    // suppress noise
            }
        }

        // ---------- 3️⃣ CLARITY BOOST (HP emphasis) ----------
        var prev = 0f
        val alpha = 0.85f

        for (i in out.indices) {
            val hp = out[i] - alpha * prev
            prev = out[i]
            out[i] = (out[i] + 0.6f * hp)
                .coerceIn(-1f, 1f)
        }

        return out
    }
}
