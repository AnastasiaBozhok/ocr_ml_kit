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


//tutorial ml-kit https://developers.google.com/ml-kit/vision/text-recognition/android

class MainActivity : AppCompatActivity() {

    // --------------------------------
    // Settings and data
    // --------------------------------

    // Ml-Kit settings
//    private val angles = intArrayOf(0, 90, 180, 270)
    private val angles = intArrayOf(0, 270)

    // Data variables
    // <image rotation angle, recognized text for the current angle>
    private var recognition_results: Map<Int, OcrResultAdapter> = mapOf()

    // Ml-Kit data variables
    private var mlkit_finished_flag = false
    private var image: InputImage? = null

    // Tesseract settings
    private val DATA_PATH =
        Environment.getExternalStorageDirectory().toString() + "/tesseract4/best/"
    private val TESSDATA = "tessdata"
    private val lang = "fra+eng"
    private val engine = TessBaseAPI.OEM_TESSERACT_LSTM_COMBINED
    private val page_segmentation_mode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD.toString()

    // --------------------------------
    // Start application and read image
    // --------------------------------

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

        prepareDataForOcr(false)

        if (null != image) {
            imageView.setImageBitmap(image!!.bitmapInternal)

            // TODO: 19/10/2020 (Anastasia) attention! there might be a conflict
            // when TextRecognition is called for a new image orientation,
            // but the results of a previous angle are not yet received
            // make a recursion ?
            for (angle in angles) {
                recognizeImageMlKit(image!!, angle)
            }

            // TODO: 19/10/2020 (Anastasia) attention! the function displayRecognitionResult
            // should be called after the TextRecognition is finished for all angles
            displayMlKitRecognitionResult()
            (Handler()).postDelayed(
                this::displayMlKitRecognitionResult,
                (2 * sqrt((image!!.width * image!!.height).toDouble()) * angles.size).toLong()
            )
        }
    }

    private fun recognizeImageMlKit(image: InputImage, angle: Int) {
        if (null != image) {

            // get the image with specified orientation
            val image_rotated = InputImage.fromBitmap(image.bitmapInternal!!, angle)

            // recognize it
            val recognizer = TextRecognition.getClient()
            recognizer.process(image_rotated)
                .addOnSuccessListener { mlkit_result ->

                    // treat the results
                    treatMlkitRecognitionResult(mlkit_result, angle, image_rotated.width, image_rotated.height)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }
    }

    private fun treatMlkitRecognitionResult(
        mlkit_result: Text?, angle: Int, width: Int, height: Int) {
        if (null != mlkit_result) {

            // Transform the result to common format
            var recognition_result = OcrResultAdapter(mlkit_result.textBlocks, angle, width, height)

            // Save the result in a global variable
            recognition_results += Pair(angle, recognition_result)


            // TODO: 22/10/2020 (Anastasia) make it notify the main thread
            // to know if the processing is finished
            // or launch the next orientation recognition here
            if (recognition_results.size == angles.size)
                mlkit_finished_flag = true
        }
    }

    //------------------------------------------
    // Tesseract OCR
    //------------------------------------------

    private fun ocrTesseractButtonClickedHandler() {

        prepareDataForOcr(true)

        if (null != image && null != image!!.bitmapInternal) {
            var bitmap = image!!.bitmapInternal
//            bitmap = rotateBitmap(bitmap!!, 270f)
            imageView.setImageBitmap(bitmap)

            for (angle in angles) {

                // Get the result in a common format
                var recognition_result = doOcrTesseract(bitmap!!, angle)
                if (null != recognition_result) {

                    // Save the result in a global variable
                    recognition_results += Pair(angle, recognition_result)
                }
            }

            tv.text = getTextResultsToDisplay()
            imageView.setImageBitmap(getAnnotatedBitmap(recognition_results))
        }
    }

    private fun prepareDataForOcr(tesseract_flag: Boolean = false) {
        image = readImage()
        recognition_results = emptyMap()

        if (tesseract_flag) {
            prepareTesseract()
        } else {
            mlkit_finished_flag = false
        }
    }

    /**
     * don't run this code in main thread - it stops UI thread. Create AsyncTask instead.
     * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
     *
     * @param image - original image to analyze
     * @param angle - image rotation angle
     */
    private fun doOcrTesseract(image: Bitmap, angle: Int): OcrResultAdapter? {
        val width_original = image.width
        val height_original = image.height
        val image = rotateBitmap(image, angle)

        return try {
            extractTextTesseract(image, angle, width_original, height_original)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message!!)
            null
        }
    }

    fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun extractTextTesseract(bitmap: Bitmap, angle: Int, width: Int, height: Int): OcrResultAdapter? {
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

        // Engine
        tessBaseApi?.init(DATA_PATH, lang, engine)

        //blackList
        tessBaseApi?.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!#%^&+=}{'\"\\|~`")

        // default tessedit_pageseg_mode assumes a single uniform block of vertically aligned text
        tessBaseApi?.setVariable("tessedit_pageseg_mode", page_segmentation_mode)

        Log.d(TAG, "Training file loaded")
        tessBaseApi?.setImage(bitmap)
        try {
            // GetUTF8Text calls Recognize behind the scene
            tessBaseApi?.getUTF8Text().orEmpty()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in recognizing text.")
        }

        return try {
            OcrResultAdapter(tessBaseApi, angle, image!!.width, image!!.height)
        } catch (e: Exception) {
            Log.e(TAG, "Error in RecognitionResultAdapter from tesseract constructor (probably in parsing tesseract horc result).")
            null
        } finally {
            tessBaseApi?.end()
        }
    }

    //------------------------------------------
    // Display results (Ml-Kit)
    //------------------------------------------

    private fun displayMlKitRecognitionResult() {
        tv.text = getTextResultsToDisplay()
        imageView.setImageBitmap(getAnnotatedBitmap(recognition_results))
    }

    private fun getAnnotatedBitmap(result_for_orientations: Map<Int, OcrResultAdapter>): Bitmap? {

        val workingBitmap: Bitmap? = imageView.drawable.toBitmap()
        val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        //Draw the image bitmap into the cavas
        val canvas = mutableBitmap?.let { Canvas(it) }

        val colors = listOf<Int>(Color.RED, Color.BLUE, Color.MAGENTA, Color.CYAN)

        // Get different images orientations
        for ((i, result_orientation_i) in result_for_orientations.values.withIndex()) {
            result_orientation_i.drawBoxesOnCanvas(canvas, colors[i])
        }

        return mutableBitmap
    }

    private fun getTextResultsToDisplay(): String {

        var text_to_display:String = ""
        for (angle in angles) {
            text_to_display += "Orientation $angle: \n"

            var recognition_result_angle = recognition_results[angle]
            var angleTexts: String = recognition_result_angle?.filteredBlockText().orEmpty()

            if (angleTexts.length > 1)
                text_to_display += angleTexts
            text_to_display += "\n"
        }
        return text_to_display
    }



    //------------------------------------------
    // Display results (common)
    //------------------------------------------


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