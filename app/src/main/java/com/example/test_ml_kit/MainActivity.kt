package com.example.test_ml_kit

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.test_ml_kit.CoordinatesRotationUtils.Companion.rotateBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*


//tutorial ml-kit https://developers.google.com/ml-kit/vision/text-recognition/android

class MainActivity : AppCompatActivity() {

    // --------------------------------
    // Settings and data
    // --------------------------------

    // Image orientations to analyze
//    private val orientations_to_treat = intArrayOf(0, 90, 180, 270)
    private val orientations_to_treat = intArrayOf(0, 270)

    // Data variables
    private var image: InputImage? = null
    // <image rotation angle, recognized text for the current angle>
    private var recognition_results_mlkit: Map<Int, OcrResultAdapter> = mapOf()
    // Tesseract model
    private var tessBaseApi: TessBaseAPI? = null

    // Tesseract settings
    private val DATA_PATH =
        Environment.getExternalStorageDirectory().toString() + "/tesseract4/best/"
    private val TESSDATA = "tessdata"
    private val lang = "fra+eng"
    private val engine = TessBaseAPI.OEM_TESSERACT_LSTM_COMBINED
    private val page_segmentation_mode = TessBaseAPI.PageSegMode.PSM_AUTO_ONLY.toString()
    // Examples of execution times (excluding model init)
    // for the file barcodeExampleSmall.png :
    // OEM_TESSERACT_LSTM_COMBINED + PSM_AUTO --> 1.42s
    // OEM_TESSERACT_LSTM_COMBINED + PSM_AUTO_OSD --> 1.42s
    // OEM_TESSERACT_LSTM_COMBINED + PSM_AUTO_ONLY --> 1.42s
    // OEM_TESSERACT_LSTM_COMBINED + PSM_SPARSE_TEXT_OSD --> 1.92s
    // OEM_TESSERACT_LSTM_COMBINED + PSM_SPARSE_TEXT --> 1.24s (but results are less accurate)
    // OEM_TESSERACT_ONLY + PSM_SPARSE_TEXT --> 4.3s
    // OEM_TESSERACT_ONLY + PSM_AUTO_ONLY --> 5.6s
    // OEM_LSTM_ONLY + PSM_AUTO_ONLY --> 8.5s

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

    private fun prepareDataForOcr(tesseract_flag: Boolean = false) {
        image = readImage()
        recognition_results_mlkit = emptyMap()

        if (tesseract_flag) {
            initializeTesseractModelIfNull()
        }
    }

    //------------------------------------------
    // Ml-kit OCR
    //------------------------------------------

    private fun ocrMlkitButtonClickedHandler() {
        val begin = System.nanoTime()

        prepareDataForOcr(false)

        if (null != image) {
            imageView.setImageBitmap(image!!.bitmapInternal)

            // recognize image for the first image orientation,
            // call next orientations and display the results
            recognizeImageMlKit(image!!, 0)
        }

        val end = System.nanoTime()
        Log.d(TAG, "ocrMlkitButtonClickedHandler Elapsed Time in seconds: ${(end - begin) * 1e-9}")
    }

    private fun recognizeImageMlKit(image: InputImage, angle_index: Int) {
        // a recursive function

        if (null != image) {

            // get current image orientation
            val angle = orientations_to_treat[angle_index]

            // get the image with specified orientation
            val image_rotated = InputImage.fromBitmap(image.bitmapInternal!!, angle)

            // recognize it
            val recognizer = TextRecognition.getClient()
            recognizer.process(image_rotated)
                .addOnSuccessListener { mlkit_result ->

                    // treat the current image orientation result
                    treatMlkitRecognitionResult(
                        mlkit_result,
                        angle,
                        image_rotated.width,
                        image_rotated.height
                    )

                    if (angle_index == orientations_to_treat.lastIndex) {
                        // TextRecognition is finished for all angles
                        displayRecognitionResult(recognition_results_mlkit)
                    } else {
                        // TextRecognition is called for a new image orientation
                        recognizeImageMlKit(image, angle_index + 1)
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }
    }

    private fun treatMlkitRecognitionResult(
        mlkit_result: Text?, angle: Int, width: Int, height: Int
    ) {
        if (null != mlkit_result) {
            // Transform the result to a common format
            var recognition_result = OcrResultAdapter(mlkit_result.textBlocks, angle, width, height)

            // Save the result in a global variable
            recognition_results_mlkit += Pair(angle, recognition_result)
        }
    }

    //------------------------------------------
    // Tesseract OCR
    //------------------------------------------

    private fun ocrTesseractButtonClickedHandler() {
        val begin = System.nanoTime()

        prepareDataForOcr(true)

        if (null != image && null != image!!.bitmapInternal) {

            var recognition_results: Map<Int, OcrResultAdapter> = mapOf()
            var bitmap = image!!.bitmapInternal
            imageView.setImageBitmap(bitmap)

            for (angle in orientations_to_treat) {

                // Get the result in a common format
                var recognition_result = doOcrTesseract(bitmap!!, angle)
                if (null != recognition_result) {

                    // Save the result in a local variable
                    recognition_results += Pair(angle, recognition_result)
                }
            }

            displayRecognitionResult(recognition_results)
        }

        val end = System.nanoTime()
        Log.d(
            TAG,
            "ocrTesseractButtonClickedHandler Elapsed Time in seconds: ${(end - begin) * 1e-9}"
        )
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
            analyzeImageTesseract(image, angle, width_original, height_original)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message!!)
            null
        }
    }

    private fun analyzeImageTesseract(bitmap: Bitmap, angle: Int, width: Int, height: Int): OcrResultAdapter? {

        // Set the image to analyze
        tessBaseApi?.setImage(bitmap)
        try {
            // GetUTF8Text calls Recognize behind the scene
            tessBaseApi?.getUTF8Text().orEmpty()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in recognizing text.")
        }

        // Convert the result to a common format
        return try {
            OcrResultAdapter(tessBaseApi, angle, image!!.width, image!!.height)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error in RecognitionResultAdapter from tesseract constructor (probably in parsing tesseract horc result)."
            )
            null
        } finally {
            // Frees up recognition results and any stored image data
            tessBaseApi?.clear()
        }
    }

    private fun initializeTesseractModelIfNull() {
        if (null == tessBaseApi) {

            prepareTesseract()

            try {
                tessBaseApi = TessBaseAPI()
            } catch (e: java.lang.Exception) {
                Log.e(TAG, e.message)
                if (tessBaseApi == null) {
                    Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.")
                }
            }

            // Init with specific languages and engine
            tessBaseApi?.init(DATA_PATH, lang, engine)
            // BlackList
            tessBaseApi?.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!#%^&+=}{'\"\\|~`")
            // Page segmentation mode, see [TessBaseAPI.PageSegMode]
            // default mode assumes a single uniform block of text
            tessBaseApi?.setVariable("tessedit_pageseg_mode", page_segmentation_mode)

//            // Disable vertical detection
//            tessBaseApi?.setVariable("textord_tabfind_vertical_text", "0")
//            tessBaseApi?.setVariable("textord_tabfind_vertical_horizontal_mix", "0")
//            tessBaseApi?.setVariable("textord_tabfind_vertical_text_ratio", "0")
//            tessBaseApi?.setVariable("textord_tabvector_vertical_box_ratio", "0")

            // classify_max_slope 	2.41421 	Slope above which lines are called vertical



            Log.d(TAG, "Training file loaded")
        }
    }

    //------------------------------------------
    // Display results
    //------------------------------------------

    private fun displayRecognitionResult(recognition_results: Map<Int, OcrResultAdapter>) {
        tv.text = getTextResultsToDisplay(recognition_results)
        imageView.setImageBitmap(getAnnotatedBitmap(recognition_results))
    }

    private fun getAnnotatedBitmap(recognition_results: Map<Int, OcrResultAdapter>): Bitmap? {

        val workingBitmap: Bitmap? = imageView.drawable.toBitmap()
        val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        //Draw the image bitmap into the cavas
        val canvas = mutableBitmap?.let { Canvas(it) }

        val colors = listOf<Int>(Color.RED, Color.BLUE, Color.MAGENTA, Color.CYAN)

        // Get different images orientations
        for ((i, result_orientation_i) in recognition_results.values.withIndex()) {
            result_orientation_i.drawBoxesOnCanvas(canvas, colors[i])
        }

        return mutableBitmap
    }

    private fun getTextResultsToDisplay(recognition_results: Map<Int, OcrResultAdapter>): String {

        var text_to_display:String = ""
        for (angle in orientations_to_treat) {
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
    // Copy Tesseract model files to the device
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