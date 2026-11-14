package com.aiphotographer.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.aiphotographer.camera.CapturedPhoto
import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.imaging.EnhancementPlan
import com.aiphotographer.intelligence.imaging.ImageEnhancer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a captured frame into a finished photograph on disk.
 *
 * The enhancement is non-destructive by default: the untouched original is
 * written alongside the enhanced version, so a judgement the app got wrong is
 * never a photograph the user has lost.
 */
class PhotoProcessor(
    private val context: Context,
    /** Write the untouched file alongside the enhanced one. */
    private val keepOriginal: Boolean = true,
) {

    data class Saved(
        val uri: Uri,
        val originalUri: Uri?,
        /** What the enhancement did, in plain words, for the UI to show. */
        val summary: String?,
    )

    suspend fun enhanceAndSave(
        photo: CapturedPhoto,
        guidance: Guidance?,
        faces: List<Box>,
        enhance: Boolean = true,
    ): Result<Saved> = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = photo.bitmap
            val baseName = FILE_NAME_FORMAT.format(Date())

            // Write the original first: once the pixels are enhanced in place,
            // the original is gone.
            val originalUri = if (enhance && keepOriginal) {
                runCatching {
                    save(bitmap, "${baseName}_original", photo.cameraState)
                }.getOrNull()
            } else {
                null
            }

            val summary = if (enhance) {
                runCatching { enhanceInPlace(bitmap, guidance, faces) }
                    .onFailure { Log.w(TAG, "Enhancement failed; saving the original", it) }
                    .getOrNull()
            } else {
                null
            }

            val uri = save(bitmap, baseName, photo.cameraState)
            Saved(uri = uri, originalUri = originalUri, summary = summary)
        }
    }

    /**
     * Apply the enhancement to [bitmap] in place.
     *
     * A 12-megapixel photograph is about 48 MB of pixels, and the pipeline
     * needs a working copy, so this is the most memory-hungry moment in the
     * app. Running out is handled rather than crashing: the caller keeps the
     * unenhanced photograph.
     */
    private fun enhanceInPlace(
        bitmap: Bitmap,
        guidance: Guidance?,
        faces: List<Box>,
    ): String {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return ""

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val measurement = ImageEnhancer.measure(pixels, width, height)

        var plan = if (guidance != null) {
            ImageEnhancer.planFor(guidance, faces)
        } else {
            // No analysis available: a cautious, scene-agnostic tidy-up.
            EnhancementPlan(
                shadowLift = 0.2f,
                highlightRecovery = 0.15f,
                vibrance = 0.12f,
                clarity = 0.2f,
                summary = "Gentle tone and colour balance",
            )
        }
        plan = ImageEnhancer.withMeasuredLevels(
            plan = plan,
            p5 = measurement.p5,
            p95 = measurement.p95,
            clippedShadows = measurement.clippedShadows,
            clippedHighlights = measurement.clippedHighlights,
            meanR = measurement.meanR,
            meanG = measurement.meanG,
            meanB = measurement.meanB,
        )
        plan = ImageEnhancer.withClarityRadius(plan, width, height)

        ImageEnhancer.apply(pixels, width, height, plan)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return plan.summary
    }

    // -----------------------------------------------------------------
    // Saving
    // -----------------------------------------------------------------

    private fun save(bitmap: Bitmap, name: String, cameraState: CameraState?): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bitmap, name, cameraState)
        } else {
            saveToPublicDirectory(bitmap, name, cameraState)
        }

    private fun saveViaMediaStore(
        bitmap: Bitmap,
        name: String,
        cameraState: CameraState?,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create a file in the gallery")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    error("Could not encode the photograph")
                }
            } ?: error("Could not open the gallery file for writing")

            // EXIF has to be written after the pixels, into the finished file.
            runCatching {
                resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                    writeExif(ExifInterface(descriptor.fileDescriptor), cameraState)
                }
            }.onFailure { Log.w(TAG, "Could not write EXIF", it) }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(
        bitmap: Bitmap,
        name: String,
        cameraState: CameraState?,
    ): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create the album folder")
        }
        val file = File(directory, "$name.jpg")
        FileOutputStream(file).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                error("Could not encode the photograph")
            }
        }
        runCatching { writeExif(ExifInterface(file.absolutePath), cameraState) }
            .onFailure { Log.w(TAG, "Could not write EXIF", it) }

        // Make it visible to the gallery.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return Uri.fromFile(file)
    }

    /**
     * Record how the photograph was taken.
     *
     * Writing the real ISO and shutter speed means the file carries the same
     * information a camera would record, which matters to anyone who later
     * wants to know why a shot looks the way it does.
     */
    private fun writeExif(exif: ExifInterface, cameraState: CameraState?) {
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "AI Photographer")
        exif.setAttribute(
            ExifInterface.TAG_DATETIME,
            EXIF_DATE_FORMAT.format(Date()),
        )
        if (cameraState != null) {
            exif.setAttribute(
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                cameraState.iso.toString(),
            )
            val seconds = cameraState.exposureTimeNs / 1_000_000_000.0
            if (seconds > 0) {
                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, seconds.toString())
            }
            if (cameraState.aperture > 0f) {
                exif.setAttribute(ExifInterface.TAG_F_NUMBER, cameraState.aperture.toString())
            }
            if (cameraState.focalLengthMm > 0f) {
                // EXIF wants a rational; a hundredth of a millimetre is plenty.
                val hundredths = (cameraState.focalLengthMm * 100).toInt()
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$hundredths/100")
            }
        }
        exif.saveAttributes()
    }

    companion object {
        private const val TAG = "PhotoProcessor"
        private const val ALBUM = "AI Photographer"
        private const val JPEG_QUALITY = 95

        private val FILE_NAME_FORMAT =
            SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss", Locale.US)
        private val EXIF_DATE_FORMAT =
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    }
}
