package com.example.test_ml_kit

import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.googlecode.tesseract.android.TessBaseAPI
import hocr4j.Page
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class RecognitionResultAdapter {

    var text_blocks: MutableList<TextBlock> = ArrayList<TextBlock>()

    class TextBlock {
        var block_text: String by Delegates.notNull()
        var block_bounding_box: Rect? = null
        var text_lines: MutableList<TextBase> = ArrayList<TextBase>()
        var block_confidence: Float? = null

        constructor(mlkit_block: Text.TextBlock) {
            block_text = mlkit_block.text
            block_bounding_box = mlkit_block.boundingBox
            for (line in mlkit_block.lines) {
                text_lines.add(TextBase(line))
            }
        }

        constructor(recognized_text: String, bounding_box: Rect?, block_confidence: Float? = null) {
            this.block_text = recognized_text
            this.block_bounding_box = bounding_box
            this.block_confidence = block_confidence
        }

        fun getFilteredText(use_filter_flag: Boolean): String {
            val line_length_threshold = 2;

            if (!use_filter_flag || this.text_lines.isEmpty()) {
                var block_text_to_display = ""
                if (null!= this.block_confidence) {
                    block_text_to_display += "[Block confidence: ${this.block_confidence!!.roundToInt()}] \n"
                }
                block_text_to_display += this.block_text + "\n"
                return block_text_to_display
            }


            var filteredText = ""
            for (line in this.text_lines) {
                if (line.recognized_text.length >= line_length_threshold)
                    filteredText += line.recognized_text + "\n"
            }
            return filteredText
        }
    }

    open class TextBase {
        var recognized_text: String by Delegates.notNull()
        var bounding_box: Rect? = null

        constructor(mlkit_line:Text.Line) {
            recognized_text = mlkit_line.text
            bounding_box = mlkit_line.boundingBox
        }
    }

    constructor(mlkit_result: MutableList<Text.TextBlock>?) {
        if (mlkit_result != null) {
            for (block in mlkit_result) {
                text_blocks.add(TextBlock(block))
            }
        }
        this.removeEmptyRecognizedText()
    }

    constructor(tesseract_result: TessBaseAPI?) {
        if (tesseract_result != null) {
            // Iterate through the results.
            val iterator = tesseract_result.resultIterator
            iterator.begin()
            do {
                val current_block_text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_BLOCK)
                val current_block_box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_BLOCK)
                val current_block_confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_BLOCK)
                text_blocks.add(TextBlock(current_block_text,current_block_box,current_block_confidence))
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_BLOCK))

            var hocr = "<body>\n"
            hocr += tesseract_result.getHOCRText(1)
            hocr += "</body>"
            var test = Page.fromHocr(mutableListOf(hocr))
//            Log.i(android.content.ContentValues.TAG, ": ${test.toString()}")

            this.removeEmptyRecognizedText()
        }
    }

    fun filteredBlockText(): String {
        return getTextToDisplay(false)
    }

    private fun getTextToDisplay(use_filter_flag: Boolean): String {
        var text_to_display = "\n"
        for (i in 0 until this.text_blocks.size) {
            var block = this.text_blocks[i]
            text_to_display += "BLOCK $i (" +
                    "${block.block_bounding_box?.left}, " +
                    "${block.block_bounding_box?.bottom}):\n"
            text_to_display += block.getFilteredText(use_filter_flag) + "\n"
        }
        return text_to_display
    }

    fun getAnnotatedBitmap(workingBitmap: Bitmap): Bitmap? {

        if (null != this) {
            val mutableBitmap = workingBitmap?.copy(Bitmap.Config.ARGB_8888, true)

            //Draw the image bitmap into the cavas
            val canvas = mutableBitmap?.let { Canvas(it) }

            this.drawBoxesOnCanvas(canvas, Color.RED)
            return mutableBitmap
        }

        return null
    }

    fun drawBoxesOnCanvas(
        canvas: Canvas?, color: Int) {

        // Line (stroke) options
        var paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2F
        paint.isAntiAlias = true
        paint.textSize = 20F;
        paint.color = color

        if (null != canvas && null != this) {
            for (i in this.text_blocks.indices) {
                val block = this.text_blocks[i]
                if (block?.block_bounding_box != null) {
                    canvas.drawRect(block.block_bounding_box, paint)
                    canvas.drawText(
                        "$i",
                        block.block_bounding_box!!.left.toFloat(),
                        block.block_bounding_box!!.top.toFloat() - 4, paint
                    )
                }
            }
        }
    }

    fun removeEmptyRecognizedText() {
        var indices_to_remove = mutableListOf<Int>()
        for (i in text_blocks.indices) {
            val block_text = text_blocks[i].block_text
            // replace("\\s".toRegex(), "") --> remove all whitespace
            if (block_text.replace("\\s".toRegex(), "").isEmpty()){
                indices_to_remove.add(i)
            }
        }
        indices_to_remove.reverse()
        for (i in indices_to_remove)
            text_blocks.removeAt(i)
    }
}