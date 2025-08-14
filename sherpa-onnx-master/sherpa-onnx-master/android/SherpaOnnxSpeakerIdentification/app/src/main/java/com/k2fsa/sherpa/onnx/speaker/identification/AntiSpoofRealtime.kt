package com.k2fsa.sherpa.onnx.speaker.identification

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class AntiSpoofRealtime(
    private val scope: CoroutineScope,
    private val sampleRate: Int = 16000,
    private val windowSamples: Int = 64600,
    private val hopMs: Int = 1000,
) {
    private val cap = windowSamples * 2
    private val ring = FloatArray(cap)
    private var write = 0
    private var totalWritten = 0L
    private var job: Job? = null
    var lastScore: Float = -1f
        private set

    fun reset() {
        write = 0
        totalWritten = 0
        lastScore = -1f
        ring.fill(0f)
    }

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            val hopSamples = (sampleRate * (hopMs / 1000.0f)).toInt().coerceAtLeast(1)
            while (true) {
                delay(hopMs.toLong())
                val have = totalWritten.coerceAtMost(cap.toLong()).toInt()
                val buf = FloatArray(windowSamples)
                if (have >= windowSamples) {
                    // take last windowSamples
                    val start = (write - windowSamples + cap) % cap
                    val first = cap - start
                    if (first >= windowSamples) {
                        System.arraycopy(ring, start, buf, 0, windowSamples)
                    } else {
                        System.arraycopy(ring, start, buf, 0, first)
                        System.arraycopy(ring, 0, buf, first, windowSamples - first)
                    }
                } else if (have > 0) {
                    // degraded mode: copy available to head, then tile or zero-fill
                    val start = (write - have + cap) % cap
                    val first = cap - start
                    if (first >= have) {
                        System.arraycopy(ring, start, buf, 0, have)
                    } else {
                        System.arraycopy(ring, start, buf, 0, first)
                        System.arraycopy(ring, 0, buf, first, have - first)
                    }
                    if (have > 500) {
                        var filled = have
                        while (filled < windowSamples) {
                            val toCopy = minOf(have, windowSamples - filled)
                            System.arraycopy(buf, 0, buf, filled, toCopy)
                            filled += toCopy
                        }
                    } else {
                        java.util.Arrays.fill(buf, have, windowSamples, 0f)
                    }
                } else {
                    // nothing yet
                    continue
                }
                try {
                    lastScore = AntiSpoof.run(buf, sampleRate)
                    Log.i(TAG, "AntiSpoofRealtime score=$lastScore")
                } catch (e: Throwable) {
                    Log.e(TAG, "AntiSpoofRealtime run error", e)
                    lastScore = -1f
                }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    fun accept(samples: FloatArray) {
        // MyApplication7 stores raw PCM short in float (no normalization to [-1,1])
        // Home.kt currently provides [-1,1] floats; convert back to short-scale to match
        for (i in samples.indices) {
            val v = (samples[i] * 32768f)
            ring[write] = v
            write = (write + 1) % cap
            totalWritten++
        }
    }
}

