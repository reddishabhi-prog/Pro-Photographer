package com.aiphotographer.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import com.aiphotographer.intelligence.camera.CameraCapabilities

/**
 * Asks the hardware what it can do, instead of assuming.
 *
 * Phone cameras differ enormously: some expose full manual sensor control,
 * many expose none, and plenty report a focus range of zero because the lens
 * is fixed. Offering a control the sensor will silently ignore is worse than
 * not offering it, so Pro mode is built from this.
 */
@OptIn(ExperimentalCamera2Interop::class)
object CameraCapabilitiesReader {

    fun read(cameraInfo: CameraInfo, hdrAvailable: Boolean = false): CameraCapabilities {
        val fallback = CameraCapabilities.UNKNOWN

        val info = try {
            Camera2CameraInfo.from(cameraInfo)
        } catch (e: IllegalArgumentException) {
            // Not a Camera2-backed camera; everything stays automatic.
            return fallback.copy(supportsHdr = hdrAvailable)
        }

        fun <T> characteristic(key: CameraCharacteristics.Key<T>): T? =
            try {
                info.getCameraCharacteristic(key)
            } catch (e: Exception) {
                null
            }

        val capabilities =
            characteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toSet() ?: emptySet()

        val hasManualSensor = CameraMetadata
            .REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities
        val hasManualPostProcessing = CameraMetadata
            .REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capabilities
        val hasRaw = CameraMetadata
            .REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities

        val isoRange = characteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

        // Reported in diopters: 0 means the lens cannot be focused manually.
        val minFocusDistance =
            characteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        val exposureState = cameraInfo.exposureState
        val evStep = try {
            exposureState.exposureCompensationStep.toFloat()
        } catch (e: Exception) {
            1f / 3f
        }.let { if (it > 0f) it else 1f / 3f }
        val evRange = try {
            exposureState.exposureCompensationRange
        } catch (e: Exception) {
            null
        }

        val zoom = cameraInfo.zoomState.value

        return CameraCapabilities(
            isoMin = isoRange?.lower ?: fallback.isoMin,
            isoMax = isoRange?.upper ?: fallback.isoMax,
            exposureTimeMinNs = exposureRange?.lower ?: fallback.exposureTimeMinNs,
            exposureTimeMaxNs = exposureRange?.upper ?: fallback.exposureTimeMaxNs,
            focusDistanceMaxDiopters = minFocusDistance,
            evCompensationMin = (evRange?.lower ?: -2) * evStep,
            evCompensationMax = (evRange?.upper ?: 2) * evStep,
            evCompensationStep = evStep,
            supportsManualIso = hasManualSensor && isoRange != null,
            supportsManualShutter = hasManualSensor && exposureRange != null,
            supportsManualFocus = minFocusDistance > 0f,
            supportsManualWhiteBalance = hasManualPostProcessing,
            supportsRaw = hasRaw,
            supportsHdr = hdrAvailable,
            zoomMin = zoom?.minZoomRatio ?: 1f,
            zoomMax = zoom?.maxZoomRatio ?: 1f,
        )
    }
}
