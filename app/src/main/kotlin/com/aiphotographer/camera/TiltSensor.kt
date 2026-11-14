package com.aiphotographer.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aiphotographer.intelligence.DeviceTilt
import kotlin.math.atan2
import kotlin.math.hypot
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads how the phone is being held.
 *
 * A level horizon is one of the few things a camera can know for certain and
 * the user cannot, which makes it some of the most valuable advice the app
 * gives. The raw accelerometer is far too jittery to show on screen, so it is
 * low-pass filtered before anyone sees it.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val latest = AtomicReference(DeviceTilt())

    /** Smoothed gravity vector. */
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var primed = false

    /** How much of each new reading to take. Lower is smoother but laggier. */
    private val smoothing = 0.18f

    val isAvailable: Boolean get() = accelerometer != null

    fun start() {
        val sensor = accelerometer ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    /** The current tilt, safe to read from any thread. */
    fun current(): DeviceTilt = latest.get()

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return

        if (!primed) {
            gravityX = values[0]
            gravityY = values[1]
            gravityZ = values[2]
            primed = true
        } else {
            gravityX += (values[0] - gravityX) * smoothing
            gravityY += (values[1] - gravityY) * smoothing
            gravityZ += (values[2] - gravityZ) * smoothing
        }

        // Roll: how far the phone is rotated about the axis you look along.
        // Upright portrait reads zero; clockwise rotation reads positive.
        val roll = Math.toDegrees(atan2(gravityX.toDouble(), gravityY.toDouble())).toFloat()

        // Pitch: where the rear camera is pointing. The z axis comes out of
        // the SCREEN, and the rear camera faces the other way, so the sign is
        // inverted: screen-up on a table means the camera points down at -90.
        val pitch = -Math.toDegrees(
            atan2(
                gravityZ.toDouble(),
                hypot(gravityX.toDouble(), gravityY.toDouble()),
            )
        ).toFloat()

        latest.set(DeviceTilt(rollDeg = roll, pitchDeg = pitch))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
