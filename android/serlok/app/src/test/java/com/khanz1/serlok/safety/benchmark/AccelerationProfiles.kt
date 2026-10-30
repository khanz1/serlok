package com.khanz1.serlok.safety.benchmark

import java.util.Random
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class SeededRng(seed: Long) {
    private val random = Random(seed)

    fun uniform(min: Double, max: Double): Double {
        return min + random.nextDouble() * (max - min)
    }

    fun gaussian(mean: Double, sd: Double): Double {
        return mean + random.nextGaussian() * sd
    }

    fun intInclusive(min: Int, max: Int): Int {
        return min + random.nextInt(max - min + 1)
    }
}

object AccelerationProfiles {
    const val HZ = 50
    const val PERIOD_NS = 1_000_000_000L / HZ
    const val GRAVITY = 9.80665
    const val MASTER_SEED = 20260826L

    enum class Scenario(
        val id: String,
        val index: Int,
        val positive: Boolean,
    ) {
        S1("S1", 1, true),
        S2("S2", 2, true),
        S3("S3", 3, true),
        S4("S4", 4, true),
        S5("S5", 5, false),
        S6("S6", 6, false),
        S7("S7", 7, false),
        S8("S8", 8, false),
    }

    data class Trace(
        val scenario: Scenario,
        val repetition: Int,
        val seed: Long,
        val samples: List<AccelSample>,
    )

    fun seedFor(scenario: Scenario, repetition: Int): Long {
        return MASTER_SEED + scenario.index * 100L + repetition
    }

    fun generate(scenario: Scenario, repetition: Int): Trace {
        val seed = seedFor(scenario, repetition)
        val rng = SeededRng(seed)
        val buffer = SampleBuffer(rng)
        when (scenario) {
            Scenario.S1 -> profileS1(buffer, rng)
            Scenario.S2 -> profileS2(buffer, rng)
            Scenario.S3 -> profileS3(buffer, rng)
            Scenario.S4 -> profileS4(buffer, rng)
            Scenario.S5 -> profileS5(buffer, rng)
            Scenario.S6 -> profileS6(buffer, rng)
            Scenario.S7 -> profileS7(buffer, rng)
            Scenario.S8 -> profileS8(buffer, rng)
        }
        return Trace(scenario, repetition, seed, buffer.samples.toList())
    }

    fun generateAll(repetitions: Int = 20): List<Trace> {
        val traces = mutableListOf<Trace>()
        for (scenario in Scenario.entries) {
            for (rep in 0 until repetitions) {
                traces.add(generate(scenario, rep))
            }
        }
        return traces
    }

    private fun profileS1(buffer: SampleBuffer, rng: SeededRng) {
        buffer.rest(rng.uniform(0.25, 0.35), magG = 1.0, noise = 0.12)
        buffer.rest(rng.uniform(0.38, 0.48), magG = 0.05, noise = 0.08)
        buffer.spike(rng.uniform(45.0, 80.0))
        buffer.ringDown(rng.uniform(0.40, 0.60), startMps2 = rng.uniform(18.0, 28.0))
    }

    private fun profileS2(buffer: SampleBuffer, rng: SeededRng) {
        val rideSec = rng.uniform(0.90, 1.20)
        buffer.oscillate(rideSec, peakG = rng.uniform(1.20, 1.80), cadenceHz = rng.uniform(8.0, 12.0))
        buffer.spike(rng.uniform(70.0, 120.0))
        buffer.ringDown(rng.uniform(0.30, 0.50), startMps2 = rng.uniform(20.0, 35.0))
    }

    private fun profileS3(buffer: SampleBuffer, rng: SeededRng) {
        buffer.rest(rng.uniform(0.25, 0.35), magG = 1.0, noise = 0.12)
        buffer.spike(rng.uniform(40.0, 70.0))
        buffer.rest(rng.uniform(0.12, 0.22), magG = 1.0, noise = 0.18)
        val extraHits = rng.intInclusive(4, 8)
        val rollWindowSec = 3.0
        val gap = rollWindowSec / extraHits
        for (i in 0 until extraHits) {
            buffer.spike(rng.uniform(25.0, 55.0))
            val remaining = extraHits - 1 - i
            if (remaining >= 0) {
                buffer.rest(rng.uniform(gap * 0.55, gap * 0.95), magG = 1.0, noise = 0.20)
            }
        }
        buffer.rest(rng.uniform(0.20, 0.40), magG = 1.0, noise = 0.12)
    }

    private fun profileS4(buffer: SampleBuffer, rng: SeededRng) {
        buffer.rest(rng.uniform(0.40, 0.60), magG = 1.0, noise = 0.10)
        val jerkSec = rng.uniform(0.08, 0.15)
        val peak = rng.uniform(30.0, 55.0)
        buffer.jerk(jerkSec, peakMps2 = peak)
        buffer.rest(rng.uniform(0.40, 0.70), magG = 1.0, noise = 0.12)
    }

    private fun profileS5(buffer: SampleBuffer, rng: SeededRng) {
        buffer.rest(rng.uniform(0.15, 0.25), magG = 1.0, noise = 0.10)
        buffer.oscillate(
            durationSec = 10.0,
            peakG = rng.uniform(1.60, 2.80),
            cadenceHz = rng.uniform(2.50, 3.00),
        )
        buffer.rest(rng.uniform(0.15, 0.30), magG = 1.0, noise = 0.10)
    }

    private fun profileS6(buffer: SampleBuffer, rng: SeededRng) {
        buffer.oscillate(
            durationSec = rng.uniform(5.5, 6.5),
            peakG = rng.uniform(1.20, 1.60),
            cadenceHz = rng.uniform(1.80, 2.20),
        )
        buffer.oscillate(
            durationSec = rng.uniform(5.5, 6.5),
            peakG = rng.uniform(1.80, 2.30),
            cadenceHz = rng.uniform(2.00, 2.40),
        )
    }

    private fun profileS7(buffer: SampleBuffer, rng: SeededRng) {
        buffer.rest(rng.uniform(0.30, 0.50), magG = 1.0, noise = 0.10)
        buffer.jerk(rng.uniform(0.20, 0.30), peakMps2 = rng.uniform(15.0, 35.0))
        buffer.rest(rng.uniform(0.40, 0.60), magG = 1.0, noise = 0.12)
    }

    private fun profileS8(buffer: SampleBuffer, rng: SeededRng) {
        buffer.oscillate(
            durationSec = 8.0,
            peakG = rng.uniform(1.30, 2.60),
            cadenceHz = rng.uniform(5.0, 12.0),
        )
    }
}

private class SampleBuffer(private val rng: SeededRng) {
    val samples = mutableListOf<AccelSample>()
    private var tNs = 0L

    fun rest(durationSec: Double, magG: Double, noise: Double) {
        val count = sampleCount(durationSec)
        repeat(count) {
            pushMagnitude(magG, noise)
        }
    }

    fun spike(amplitudeMps2: Double) {
        val x = rng.uniform(-1.0, 1.0)
        val y = rng.uniform(-1.0, 1.0)
        val z = rng.uniform(0.2, 1.0)
        val norm = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6)
        val scale = amplitudeMps2 / norm
        pushVector(x * scale, y * scale, z * scale)
    }

    fun jerk(durationSec: Double, peakMps2: Double) {
        val count = sampleCount(durationSec).coerceAtLeast(2)
        for (i in 0 until count) {
            val phase = i.toDouble() / (count - 1).coerceAtLeast(1)
            val envelope = sin(PI * phase)
            val mag = GRAVITY + (peakMps2 - GRAVITY) * envelope
            val x = rng.gaussian(0.0, 0.8)
            val y = rng.gaussian(0.0, 0.8)
            pushVector(x, y, mag)
        }
    }

    fun ringDown(durationSec: Double, startMps2: Double) {
        val count = sampleCount(durationSec)
        repeat(count) { i ->
            val frac = (i + 1).toDouble() / count.coerceAtLeast(1)
            val mag = startMps2 * (1.0 - frac) + GRAVITY * frac
            pushVector(
                rng.gaussian(0.0, 0.25),
                rng.gaussian(0.0, 0.25),
                mag,
            )
        }
    }

    fun oscillate(durationSec: Double, peakG: Double, cadenceHz: Double) {
        val count = sampleCount(durationSec)
        repeat(count) { i ->
            val tSec = i.toDouble() / AccelerationProfiles.HZ
            val phase = sin(2.0 * PI * cadenceHz * tSec)
            val magG = 1.0 + (peakG - 1.0) * (0.5 + 0.5 * phase)
            pushMagnitude(magG, noise = 0.18)
        }
    }

    private fun sampleCount(durationSec: Double): Int {
        return (durationSec * AccelerationProfiles.HZ).roundToInt().coerceAtLeast(1)
    }

    private fun pushMagnitude(magG: Double, noise: Double) {
        val x = rng.gaussian(0.0, noise)
        val y = rng.gaussian(0.0, noise)
        val z = magG * GRAVITY + rng.gaussian(0.0, noise)
        pushVector(x, y, z)
    }

    private fun pushVector(x: Double, y: Double, z: Double) {
        samples.add(
            AccelSample(
                x = x.toFloat(),
                y = y.toFloat(),
                z = z.toFloat(),
                timestampNs = tNs,
            )
        )
        tNs += AccelerationProfiles.PERIOD_NS
    }
}

private const val GRAVITY = AccelerationProfiles.GRAVITY
