package com.example.test_mlkit_tesseract

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.example.test_mlkit_tesseract.CoordinatesRotationUtils.Companion.rotateBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.*
import java.util.*


//tutorial ml-kit https://developers.google.com/ml-kit/vision/text-recognition/android
// https://stackoverflow.com/questions/57479368/trouble-using-opencv-in-android
class MainActivity : AppCompatActivity() {

    // --------------------------------
    // Settings and data
    // --------------------------------

    companion object {
        private const val TAG = android.content.ContentValues.TAG
        private const val PERMISSION_REQUESTS = 1

        // Image orientations to analyze
//    private val orientations_to_treat = intArrayOf(0, 90, 180, 270)
        private val orientations_to_treat = intArrayOf(0, 270)

        // Data variables
        private var image: InputImage? = null
        // <image rotation angle, recognized text for the current angle>
        private var recognition_results_mlkit = OcrResultsAdapter()
        // Tesseract model
        private var tessBaseApi: TessBaseAPI? = null

        // Tesseract settings
        private val DATA_PATH =
            Environment.getExternalStorageDirectory().toString() + "/tesseract4/best/"
        private val TESSDATA = "tessdata"
        private val lang = "eng+fra"
        private val engine = TessBaseAPI.OEM_TESSERACT_LSTM_COMBINED
        private val page_segmentation_mode = TessBaseAPI.PageSegMode.PSM_AUTO.toString()
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

        // Debug variables
        private var begin_time_mlkit:Long = 0

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Internal OpenCV library not loaded during static initialization.")
            } else {
                Log.d(TAG, "Internal OpenCV library is loaded during static initialization.")
            }
        }
    }



    // --------------------------------
    // Start application
    // --------------------------------

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }

        initOpenCVStaticOrAsync()

        setContentView(R.layout.activity_main)
        tv.movementMethod = ScrollingMovementMethod()

        button_ocr_mlkit.setOnClickListener {ocrMlkitButtonClickedHandler()}
        button_ocr_tesseract.setOnClickListener {ocrTesseractButtonClickedHandler()}
    }


    //------------------------------------------
    // Load Open CV
    //------------------------------------------

    override fun onResume() {
        super.onResume()
        initOpenCVStaticOrAsync()
    }

    private fun initOpenCVStaticOrAsync() {
        /* This will pause the main thread until the OpenCV library is load */
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                TAG,
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            )
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i("OpenCV", "OpenCV loaded successfully")
                    val imageMat = Mat() // To test if OpenCV loaded correctly
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    //------------------------------------------
    // Load and pre-treat the image to analyze
    //------------------------------------------

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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun prepareDataForOcr(tesseract_flag: Boolean = false) {
        image = readImage()

        if (null != image) {
            // TODO: 28/10/2020 (Anastasia)
            // The code below should be called asynchronously !!!
            image = ImagePretreatment.pretreatImage(image)

            recognition_results_mlkit.empty()

            if (tesseract_flag) {
                initializeTesseractModelIfNull()
            }
        }
    }

    //------------------------------------------
    // Ml-kit OCR
    //------------------------------------------

    @RequiresApi(Build.VERSION_CODES.N)
    private fun ocrMlkitButtonClickedHandler() {
        begin_time_mlkit = System.nanoTime()

        prepareDataForOcr(false)

        if (null != image) {
            imageView.setImageBitmap(image!!.bitmapInternal)

            // recognize image for the first image orientation,
            // call next orientations and display the results
            recognizeImageMlKit(image!!, 0)
        }
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
                        recognition_results_mlkit.displayRecognitionResult(tv, imageView)

                        val end = System.nanoTime()
                        Log.d(
                            TAG,
                            "ocrMlkitButtonClickedHandler Elapsed Time in seconds: ${(end - begin_time_mlkit) * 1e-9}"
                        )
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
            recognition_results_mlkit.addResultForImageOrientation(angle, recognition_result)
        }
    }

    //------------------------------------------
    // Tesseract OCR
    //------------------------------------------

    @RequiresApi(Build.VERSION_CODES.N)
    private fun ocrTesseractButtonClickedHandler() {
        val begin = System.nanoTime()

        prepareDataForOcr(true)

        if (null != image && null != image!!.bitmapInternal) {

            var recognition_results = OcrResultsAdapter()
            var bitmap = image!!.bitmapInternal
            imageView.setImageBitmap(bitmap)

            for (angle in orientations_to_treat) {

                // Get the result in a common format
                var recognition_result = doOcrTesseract(bitmap!!, angle)
                if (null != recognition_result) {

                    // Save the result in a local variable
                    recognition_results.addResultForImageOrientation(angle, recognition_result)
                }
            }

            recognition_results.displayRecognitionResult(tv, imageView)
        }

        val end = System.nanoTime()
        Log.d(
            TAG,
            "ocrTesseractButtonClickedHandler Elapsed Time in seconds: ${(end - begin) * 1e-9}"
        )
    }

    // TODO: 28/10/2020 (Anastasia)
    // Create AsyncTask
    /**
     * don't run this code in main thread - it pauses UI thread. Create AsyncTask instead.
     * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
     *
     * @param image - original image to analyze
     * @param angle - image rotation angle
     */
    private fun doOcrTesseract(image: Bitmap, angle: Int): OcrResultAdapter? {
        val image = rotateBitmap(image, angle)

        return try {
            analyzeImageTesseract(image, angle)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message!!)
            null
        }
    }

    private fun analyzeImageTesseract(bitmap: Bitmap, angle: Int): OcrResultAdapter? {

        // Set the image to analyze
        tessBaseApi?.setImage(bitmap)
        val begin = System.nanoTime()
        try {
            // GetUTF8Text calls Recognize behind the scene
            tessBaseApi?.getUTF8Text().orEmpty()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in recognizing text.")
        }
        val end = System.nanoTime()
        Log.d(
            TAG,
            "Tesseract recognition, single image. Elapsed Time in seconds: ${(end - begin) * 1e-9}"
        )

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
                e.message?.let { Log.e(TAG, it) }
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

//            // Disable vertical detection (test)
//            tessBaseApi?.setVariable("textord_tabfind_vertical_text", "0")
//            tessBaseApi?.setVariable("textord_tabfind_vertical_horizontal_mix", "1")
//            tessBaseApi?.setVariable("textord_tabfind_vertical_text_ratio", "0")
//            tessBaseApi?.setVariable("textord_tabvector_vertical_box_ratio", "0")
//            tessBaseApi?.setVariable("textord_straight_baselines", "1")
//            tessBaseApi?.setVariable("textord_old_baselines", "0")
//            tessBaseApi?.setVariable("textord_tabfind_force_vertical_text", "1")
//            tessBaseApi?.setVariable("textord_fast_pitch_test", "1")

            // classify_max_slope 	2.41421 	Slope above which lines are called vertical
            // textord_tabvector_vertical_gap_fraction 	0.5 	max fraction of mean blob width allowed for vertical gaps in vertical text
            // chop_vertical_creep 	0 	Vertical creep


            Log.d(TAG, "Training file loaded")
        }
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



}