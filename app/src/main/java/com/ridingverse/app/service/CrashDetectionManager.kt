package com.ridingverse.app.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * 50 Hz IMU monitor (accelerometer + gyroscope via [SensorManager.SENSOR_DELAY_GAME]).
 *
 * Triggers [onImpact] when a shock above [IMPACT_G_THRESHOLD] is accompanied by
 * rotational tumbling above [TUMBLE_RAD_S_THRESHOLD] inside the same
 * [CORRELATION_WINDOW_MS] window — the signature of a real crash rather than a
 * pothole. Debounced with a 60 s cooldown so one incident fires once.
 */
class CrashDetectionManager(
    context: Context,
    private val onImpact: (impactG: Float) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile private var lastHighGAt = 0L
    @Volatile private var lastHighG = 0f
    @Volatile private var lastTriggerAt = 0L
    @Volatile private var running = false

    /**
     * Tunable impact threshold in G (default [IMPACT_G_THRESHOLD]).
     * Lower = more sensitive. Updated live from Settings.
     */
    @Volatile var gThresholdG: Float = IMPACT_G_THRESHOLD

    fun start() {
        if (running) return
        running = true
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        running = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val g = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                ) / GRAVITY
                if (g >= gThresholdG) {
                    lastHighGAt = now
                    lastHighG = g
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val w = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
                val correlated = now - lastHighGAt <= CORRELATION_WINDOW_MS
                val cooledDown = now - lastTriggerAt >= COOLDOWN_MS
                if (w >= TUMBLE_RAD_S_THRESHOLD && correlated && cooledDown) {
                    lastTriggerAt = now
                    onImpact(lastHighG)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val GRAVITY = 9.80665f
        /** Impact shock threshold. */
        const val IMPACT_G_THRESHOLD = 3.8f
        /** Rotational tumble threshold (rad/s). */
        const val TUMBLE_RAD_S_THRESHOLD = 2.5f
        /** Shock and tumble must coincide within this window. */
        const val CORRELATION_WINDOW_MS = 500L
        /** One incident triggers at most once per minute. */
        const val COOLDOWN_MS = 60_000L
    }
}
