package com.example.test_ml_kit

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import java.io.*
import java.util.*
import kotlin.math.sqrt


//tutorial https://developers.google.com/ml-kit/vision/text-recognition/android

class MainActivity : AppCompatActivity() {

    // Ml-Kit settings
//    private val angles = intArrayOf(0, 90, 180, 270)
    private val angles = intArrayOf(0, 270)

    // Ml-Kit data variables
    // <image rotation angle, recognized text for the current angle>
    private var blocks_angles: Map<Int, MutableList<Text.TextBlock>> = mapOf<Int, MutableList<Text.TextBlock>>()
    private var image: InputImage? = null

    // Tesseract settings
    private val DATA_PATH =
        Environment.getExternalStorageDirectory().toString() + "/tesseract4/best/"
    private val TESSDATA = "tessdata"
    private val lang = "eng"

    // Tesseract data variables
    private var tess_result: RecognitionResultAdapter?  = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }

        setContentView(R.layout.activity_main)
        tv.movementMethod = ScrollingMovementMethod()

        button_ocr_mlkit.setOnClickListener {ocrMlkitButtonClickedHandler()}
        button_ocr_tesseract.setOnClickListener {ocrTesseractButtonClickedHandler()}
    }

    private fun readImage(): InputImage? {
        var file: File = File("/sdcard/Download", editText.text.toString())
        var uri: Uri = Uri.fromFile(file)
        return try {
            return InputImage.fromFilePath(applicationContext, uri)
        } catch (e: IOException) {
            tv.text = e.toString()
            e.printStackTrace()
            null
        }
    }


    //------------------------------------------
    // Ml-kit OCR
    //------------------------------------------

    private fun ocrMlkitButtonClickedHandler() {
        image = readImage()

        if (null != image) {
            imageView.setImageBitmap(image!!.bitmapInternal)

            // TODO: 19/10/2020 (Anastasia) attention! there might be a conflict
            // when TextRecognition is called for a new image orientation,
            // but the results of a previous angle are not yet received
            for (angle in angles) {
                recognizeImageMlKit(image!!, angle)
            }

            // TODO: 19/10/2020 (Anastasia) attention! the function displayRecognitionResult
            // should be called after the TextRecognition is finished for all angles
            (Handler()).postDelayed(
                this::displayMlKitRecognitionResult,
                (sqrt((image!!.width * image!!.height).toDouble()) * angles.size).toLong()
            )
        }
    }

    private fun recognizeImageMlKit(image: InputImage, angle: Int) {

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

    //------------------------------------------
    // Tesseract OCR
    //------------------------------------------

    private fun ocrTesseractButtonClickedHandler() {
        image = readImage()

        if (null != image && null != image!!.bitmapInternal) {
            var bitmap = image!!.bitmapInternal
//            bitmap = rotateBitmap(bitmap!!, 270f)
            imageView.setImageBitmap(bitmap)

            val result = doOcrTesseract(bitmap!!)
//                tv.text = result
            tv.text = tess_result?.filteredBlockText()
            imageView.setImageBitmap(tess_result?.getAnnotatedBitmap(imageView.drawable.toBitmap()))
        }
    }

    private fun doOcrTesseract(image: Bitmap): String {
        prepareTesseract()
        return startOcrTesseract(image)
    }

    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * don't run this code in main thread - it stops UI thread. Create AsyncTask instead.
     * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
     *
     * @param bitmap
     */
    private fun startOcrTesseract(bitmap: Bitmap): String {
        try {
            var result = extractTextTesseract(bitmap)
            return result.orEmpty()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message!!)
            return ""
        }
    }

    private fun extractTextTesseract(bitmap: Bitmap): String? {
        var tessBaseApi: TessBaseAPI? = null
        try {
            tessBaseApi = TessBaseAPI()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message!!)
            if (tessBaseApi == null) {
                Log.e(
                    TAG,
                    "TessBaseAPI is null. TessFactory not returning tess object."
                )
            }
        }
        tessBaseApi?.init(DATA_PATH, lang)

//       //EXTRA SETTINGS
//        //For example if we only want to detect numbers
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
//
//        //blackList Example
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
//                "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");

        // default tessedit_pageseg_mode assumes a single uniform block of vertically aligned text
        tessBaseApi?.setVariable(
            "tessedit_pageseg_mode",
            TessBaseAPI.PageSegMode.PSM_AUTO_OSD.toString()
        )

        Log.d(TAG, "Training file loaded")
        tessBaseApi?.setImage(bitmap)
        var extractedText = "empty result"
        try {
            // GetUTF8Text calls Recognize behind the scene
            extractedText = tessBaseApi?.getUTF8Text().orEmpty()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in recognizing text.")
        }
        tess_result = try {
            RecognitionResultAdapter(tessBaseApi)
        } catch (e: Exception) {
            Log.e(TAG, "Error in parsing tesseract horc result.")
            null
        }

        tessBaseApi?.end()
        return extractedText
    }

    //------------------------------------------
    // Display results (Ml-Kit)
    //------------------------------------------

    private fun displayMlKitRecognitionResult() {
        tv.text = getTextResultsToDisplayMlKit()
        imageView.setImageBitmap(getAnnotatedBitmapMlKit())
    }

    private fun getAnnotatedBitmapMlKit(): Bitmap? {

        val workingBitmap: Bitmap? = imageView.drawable.toBitmap()
        val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)

        //Draw the image bitmap into the cavas
        val canvas = mutableBitmap?.let { Canvas(it) }

        val colors = listOf<Int>(Color.RED, Color.BLUE, Color.MAGENTA, Color.CYAN)
        var angle: Int = 0
        var blocks:MutableList<Text.TextBlock>? = mutableListOf()

        // Get different images orientations
        for (i in angles.indices) {
            angle = angles[i]
            RecognitionResultAdapter(blocks_angles[angle]).drawBoxesOnCanvas(canvas, colors[i])
        }

        return mutableBitmap
    }

    private fun getTextResultsToDisplayMlKit(): String {

        var text_to_display:String = ""
        for (angle in angles) {
            text_to_display += "Orientation $angle: \n"

            var recognition_result_angle = RecognitionResultAdapter(blocks_angles[angle])
            var angleTexts: String = recognition_result_angle.filteredBlockText()

            if (angleTexts.length > 1)
                text_to_display += angleTexts
            text_to_display += "\n"
        }
        return text_to_display
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

    //------------------------------------------
    // Display results (common)
    //------------------------------------------

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

    //------------------------------------------
    // Set up a Tesseract model
    //------------------------------------------

    private fun prepareTesseract() {
        try {
            prepareDirectory(DATA_PATH + TESSDATA)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        copyTessDataFiles(TESSDATA)
    }

    /**
     * Prepare directory on external storage
     *
     * @param path
     * @throws Exception
     */
    private fun prepareDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(
                    TAG,
                    "ERROR: Creation of directory $path failed, check does Android Manifest have permission to write to external storage."
                )
            }
        } else {
            Log.i(TAG, "Created directory $path")
        }
    }

    /**
     * Copy tessdata files (located on assets/tessdata) to destination directory
     *
     * @param path - name of directory with .traineddata files
     */
    private fun copyTessDataFiles(path: String) {
        try {
            val fileList = assets.list(path)
            for (fileName in fileList!!) {

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                val pathToDataFile = "$DATA_PATH$path/$fileName"
                if (!File(pathToDataFile).exists()) {
                    val `in`: InputStream = assets.open("$path/$fileName")
                    val out: OutputStream = FileOutputStream(pathToDataFile)

                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int
                    while (`in`.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                    `in`.close()
                    out.close()
                    Log.d(TAG, "Copied " + fileName + "to tessdata")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata $e")
        }
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
        private const val TAG = android.content.ContentValues.TAG
        private const val PERMISSION_REQUESTS = 1
    }

}