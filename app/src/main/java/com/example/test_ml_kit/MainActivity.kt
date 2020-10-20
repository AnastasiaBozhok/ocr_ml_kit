package com.example.test_ml_kit

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.sqrt


//tutorial https://developers.google.com/ml-kit/vision/text-recognition/android

class MainActivity : AppCompatActivity() {

    private val USE_ML_KIT_FLAG = true

//    private val angles = intArrayOf(0, 90, 180, 270)
    private val angles = intArrayOf(0, 270)
    // <image rotation angle, recognized text for the current angle>
    private var blocks_angles: Map<Int, MutableList<Text.TextBlock>> = mapOf<Int, MutableList<Text.TextBlock>>()
    private var image: InputImage? = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }

        setContentView(R.layout.activity_main)
        tv.movementMethod = ScrollingMovementMethod()

        button_ocr.setOnClickListener {ocrButtonClickedHandler()}
    }

    private fun readImage(): InputImage? {
        var file: File = File("/sdcard/Download", editText.text.toString())
        var uri: Uri = Uri.fromFile(file)
        try {
            image = InputImage.fromFilePath(applicationContext, uri)
        } catch (e: IOException) {
            tv.text = e.toString()
            e.printStackTrace()
        }
        return image
    }

    private fun getModelPath(): String {
        var file: File = File("/sdcard/Download", "eng.traineddata")
        var uri: Uri = Uri.fromFile(file)

        return uri.path.orEmpty()
    }

    //------------------------------------------
    // OCR and visualization
    //------------------------------------------

    private fun ocrButtonClickedHandler() {
        image = readImage()
        imageView.setImageBitmap(image?.bitmapInternal)

        if (null != image) {
            if (USE_ML_KIT_FLAG) {
                // TODO: 19/10/2020 (Anastasia) attention! there might be a conflict
                // when TextRecognition is called for a new image orientation,
                // but the results of a previous angle are not yet received
                for (angle in angles) {
                    recognizeImage(image!!, angle)
                }

                // TODO: 19/10/2020 (Anastasia) attention! the function displayRecognitionResult
                // should be called after the TextRecognition is finished for all angles
                (Handler()).postDelayed(
                    this::displayRecognitionResult,
                    (sqrt((image!!.width * image!!.height).toDouble()) * angles.size).toLong()
                )
            } else {
                tv.text = image!!.bitmapInternal?.let { extractText(it) }
            }
        }
    }

    private fun displayRecognitionResult() {
        tv.text = getTextResultsToDisplay()
        imageView.setImageBitmap(getAnnotatedBitmap())
    }

    private fun getAnnotatedBitmap(): Bitmap? {

        val workingBitmap: Bitmap? = imageView.drawable.toBitmap()
        val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)

        //Draw the image bitmap into the cavas
        val canvas = mutableBitmap?.let { Canvas(it) }

        // Line options
        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2F
        paint.isAntiAlias = true
        paint.textSize = 20F;

        val colors = listOf<Int>(Color.RED, Color.BLUE, Color.MAGENTA, Color.CYAN)
        var angle: Int = 0
        var blocks:MutableList<Text.TextBlock>? = mutableListOf()

        // Get rectangles for not rotated image
        for (i in angles.indices) {
            paint.color = colors[i]
            angle = angles[i]
            blocks = blocks_angles[angle]
            if (blocks != null) {
                for (i in blocks.indices) {
                    val block = blocks[i]
                    block?.boundingBox?.let {canvas?.drawRect(it, paint) }
                    block?.boundingBox?.left?.toFloat()?.let {
                        if (canvas != null) {
                            canvas.drawText(
                                "$i",
                                it, block?.boundingBox!!.top.toFloat(), paint
                            )
                        }
                    }
                }
            }
        }

        return mutableBitmap
    }

    private fun getTextResultsToDisplay(): String {
        var text_to_display:String = ""
        for (angle in angles) {
            text_to_display += "Orientation $angle: \n"
            var angleTexts: String = filteredBlockText(blocks_angles[angle])
            if (angleTexts.length > 1)
                text_to_display += angleTexts
            text_to_display += "\n"
        }
        return text_to_display
    }

    private fun filteredBlockText(blocks_angle: MutableList<Text.TextBlock>?): String {
        return blocks_angle?.let { getTextToDisplay(it, true) }.orEmpty()
    }

    private fun recognizeImage(image: InputImage, angle: Int) {

        if (null != image) {
            val image_rotated = InputImage.fromBitmap(image.bitmapInternal!!, angle)
            val recognizer = TextRecognition.getClient()
            val result = recognizer.process(image_rotated)
                .addOnSuccessListener { visionText ->
                    var blocks = visionText.textBlocks
                    blocks = adjustCoordinatesByOriginalAngle(
                        blocks, angle,
                        image_rotated.width, image_rotated.height
                    )

                    blocks_angles += Pair(angle, blocks)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }
    }


    @Throws(java.lang.Exception::class)
    private fun extractText(bitmap: Bitmap): String? {
        val tessBaseApi = TessBaseAPI()
        tessBaseApi.init(getModelPath(), "eng")
        tessBaseApi.setImage(bitmap)
        val extractedText = tessBaseApi.utF8Text
        tessBaseApi.end()
        return extractedText
    }




    private fun adjustCoordinatesByOriginalAngle(
        blocks: MutableList<Text.TextBlock>, angle: Int, width: Int, height: Int
    ):
            MutableList<Text.TextBlock> {

        if (0 != angle) {
            for (block in blocks) {
                if (null != block.cornerPoints) {
                    for (i in 0 until (block.cornerPoints!!.size)) {
                        block.cornerPoints!![i] = rotatePoint(
                            block.cornerPoints!![i],
                            angle,
                            width,
                            height
                        )
                    }
                }

                if (null != block.boundingBox) {

                    val rec = getRectangleFromCorners(block.cornerPoints)
                    block.boundingBox!!.left = rec.left
                    block.boundingBox!!.top = rec.top
                    block.boundingBox!!.right = rec.right
                    block.boundingBox!!.bottom = rec.bottom

                }
            }
        }
        return blocks
    }

    private fun getRectangleFromCorners(cornerPoints: Array<Point>?): Rect {
        var rect = Rect(0, 0, 0, 0)

        if (null != cornerPoints) {
            val left = minOf(cornerPoints[0].x, cornerPoints[1].x, cornerPoints[2].x)
            val top = minOf(cornerPoints[0].y, cornerPoints[1].y, cornerPoints[2].y)
            val right = maxOf(cornerPoints[0].x, cornerPoints[1].x, cornerPoints[2].x)
            val bottom = maxOf(cornerPoints[0].y, cornerPoints[1].y, cornerPoints[2].y)

            rect.left = left
            rect.top = top
            rect.right = right
            rect.bottom = bottom
        }

        return rect
    }

    private fun rotateXCoordinate(x: Int, y: Int, angle: Int, width: Int, height: Int): Int {

//        val angle = 360 - angle
//        val center_x = width / 2
//        val center_y = height / 2
//        return round((x - center_x) * cos(angle * Math.PI / 180) -
//                (height - y - center_y) * sin(angle * Math.PI / 180) + center_x).toInt()

            return width - y

    }

    private fun rotateYCoordinate(x: Int, y: Int, angle: Int, width: Int, height: Int): Int {
        // In an image, downwards is positive Y and rightwards is positive X
        // https://stackoverflow.com/questions/6428192/get-new-x-y-coordinates-of-a-point-in-a-rotated-image

//        val angle = 360 - angle
//        val center_x = width / 2
//        val center_y = height / 2
//        return round(
//            -(x - center_x) * sin(angle * Math.PI / 180) -
//                    (height - y - center_y) * cos(angle * Math.PI / 180) + (height - center_y)
//        ).toInt()

        return x
    }

    private fun rotatePoint(point: Point, angle: Int, width: Int, height: Int): Point {
        return Point(
            rotateXCoordinate(point.x, point.y, angle, width, height),
            rotateYCoordinate(point.x, point.y, angle, width, height)
        )
    }


    private fun getTextToDisplay(
        blocks: MutableList<Text.TextBlock>, use_filter_flag: Boolean = false
    ): String {

        var text_to_display = "\n"
        for (i in 0 until blocks.size) {
            var block = blocks[i]
            text_to_display += "BLOCK $i (" +
                    "${block.cornerPoints?.get(0)?.x}, " +
                    "${block.cornerPoints?.get(0)?.y}):\n"
            text_to_display += getFilteredText(block, use_filter_flag) + "\n"
        }
        return text_to_display
    }

    private fun getFilteredText(block: Text.TextBlock, use_filter_flag: Boolean): String {
        val line_length_threshold = 2;

        if (!use_filter_flag)
            return block.text

        var filteredText = ""
        for (line in block.lines) {
            if (line.text.length >= line_length_threshold)
                filteredText += line.text + "\n"
        }
        return filteredText
    }

    //------------------------------------------
    // Permissions
    //------------------------------------------

    private fun getRequiredPermissions(): Array<String?> {
        return try {
            val info = this.packageManager
                .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.isNotEmpty()) {
                ps
            } else {
                arrayOfNulls(0)
            }
        } catch (e: Exception) {
            arrayOfNulls(0)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions = ArrayList<String>()
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    allNeededPermissions.add(permission)
                }
            }
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
            )
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    companion object {
        private const val TAG = "ChooserActivity"
        private const val PERMISSION_REQUESTS = 1
    }

}