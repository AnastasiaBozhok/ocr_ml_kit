package com.example.test_ml_kit

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment.getExternalStorageDirectory
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.ArrayList


//tutorial https://developers.google.com/ml-kit/vision/text-recognition/android

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }

        //var file: File = File(getExternalStorageDirectory(), "read.me")
        var file: File = File("/sdcard/Download", "test.png")


        button_ocr.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                var file: File = File("/sdcard/Download", editText.text.toString())
                //Toast.makeText(applicationContext, "CLICK !", Toast.LENGTH_LONG)

                var uri: Uri = Uri.fromFile(file)

                val image: InputImage
                try {
                    image = InputImage.fromFilePath(applicationContext, uri)
                    val recognizer = TextRecognition.getClient()
                    val result = recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            tv.text = visionText.text
                            // ...
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                            // Task failed with an exception
                            // ...
                        }
                } catch (e: IOException) {
                    tv.text = e.toString()
                    e.printStackTrace()
                }
            }
        })

        //tv.text = "abc"

    }


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