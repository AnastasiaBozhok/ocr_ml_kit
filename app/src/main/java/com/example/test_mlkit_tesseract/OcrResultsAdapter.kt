package com.example.test_mlkit_tesseract

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap

class OcrResultsAdapter {

    var recognition_results: Map<Int, OcrResultAdapter> = emptyMap()

    fun empty() {
        recognition_results = emptyMap()
    }

    fun addResultForImageOrientation(angle: Int, recognition_result: OcrResultAdapter) {
        recognition_results += Pair(angle, recognition_result)
    }

    fun getTextResultsToDisplay(): String {
        val orientations_to_treat = recognition_results.keys

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

    fun getAnnotatedBitmap(workingBitmap: Bitmap): Bitmap? {
        val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        //Draw the image bitmap into the cavas
        val canvas = mutableBitmap?.let { Canvas(it) }

        // Set up a stroke color for a specific orientation
        val colors = mutableListOf<Int>()
        for (i in recognition_results.keys.indices) {
            var color = 0
            color = when (i) {
                0 -> Color.RED
                1 -> Color.BLUE
                2 -> Color.MAGENTA
                3 -> Color.CYAN
                else -> {Color.YELLOW}
            }
            colors.add(color)
        }

        // Get different images orientations
        for ((i, result_orientation_i) in recognition_results.values.withIndex()) {
            result_orientation_i.drawBoxesOnCanvas(canvas, colors[i])
        }

        return mutableBitmap
    }

    fun displayRecognitionResult(textView: TextView?, imageView: ImageView?) {
        if (null != imageView && null != imageView.drawable) {
            val workingBitmap: Bitmap = imageView.drawable.toBitmap()
            textView?.text = getTextResultsToDisplay()
            imageView.setImageBitmap(getAnnotatedBitmap(workingBitmap))
        }
    }
}