package com.example.test_ml_kit

import android.graphics.*
import com.google.mlkit.vision.text.Text
import com.googlecode.tesseract.android.TessBaseAPI
import hocr4j.Page
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class OcrResultAdapter {

    // --------------------------------------
    // Class attributes and nested classes
    // --------------------------------------

    var text_blocks: MutableList<TextBlock> = ArrayList<TextBlock>()
    var angle: Int = 0

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

    // --------------------------------------
    // Constructors from ml-kit and tesseract formats
    // --------------------------------------

    constructor(mlkit_result: MutableList<Text.TextBlock>?, angle:Int? = null) {
        this.angle = if (null == angle) 0 else angle

        if (mlkit_result != null) {
            for (block in mlkit_result) {
                text_blocks.add(TextBlock(block))
            }
        }
        this.removeBlocksWithEmptyText()
    }

    constructor(tesseract_result: TessBaseAPI?, angle:Int? = null) {
        this.angle = if (null == angle) 0 else angle

        if (tesseract_result != null) {
            // Iterate through the results.
            val iterator = tesseract_result.resultIterator
            var iterator_level = TessBaseAPI.PageIteratorLevel.RIL_BLOCK
            iterator.begin()
            do {
                val current_block_text = iterator.getUTF8Text(iterator_level)
                val current_block_box = iterator.getBoundingRect(iterator_level)
                val current_block_confidence = iterator.confidence(iterator_level)
                text_blocks.add(TextBlock(current_block_text,current_block_box,current_block_confidence))
            } while (iterator.next(iterator_level))

            var hocr = "<body>\n"
            hocr += tesseract_result.getHOCRText(1)
            hocr += "</body>"
            var test = Page.fromHocr(mutableListOf(hocr))
//            Log.i(android.content.ContentValues.TAG, ": ${test.toString()}")

            this.removeBlocksWithEmptyText()
        }
    }

    // --------------------------------------
    // Filtering results
    // --------------------------------------

    fun removeBlocksWithEmptyText() {
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

    // --------------------------------------
    // Text to display
    // --------------------------------------

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

    // -----------------------------------------
    // Visualize results on an image
    // -----------------------------------------

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

    fun drawBoxesOnCanvas(canvas: Canvas?, color: Int? = null) {

        // Line (stroke) options
        var paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2F
        paint.textSize = 20F;
        if (color != null) {
            paint.color = color
        } else {
            paint.color = Color.YELLOW
        }

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

    // TODO: 22/10/2020 (Anastasia)
    // 1) Make it more efficient (without corner --> rectangle conversion)
    // 2) Make it work with all angles (only 0 and 270 now)
    fun adjustCoordinatesByOriginalAngle(angle: Int, width: Int, height: Int): OcrResultAdapter {

        if (0 != angle) {
            for (block in this.text_blocks) {

                if (null != block.block_bounding_box) {
                    var cornerPoints = getCornersFromRectange(block.block_bounding_box!!)
                    if (null != cornerPoints) {
                        for (i in 0 until (cornerPoints.size)) {
                            cornerPoints[i] = rotatePoint(cornerPoints!![i], angle, width, height)
                        }
                    }

                    val rec = getRectangleFromCorners(cornerPoints)
                    block.block_bounding_box!!.left = rec.left
                    block.block_bounding_box!!.top = rec.top
                    block.block_bounding_box!!.right = rec.right
                    block.block_bounding_box!!.bottom = rec.bottom

                }
            }
        }
        return this
    }

    private fun getCornersFromRectange(rect: Rect): Array<Point> {
        return arrayOf(
            Point(rect.left,rect.top),
            Point(rect.right,rect.top),
            Point(rect.right,rect.bottom),
            Point(rect.left,rect.bottom))
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

        return if (270 == angle)
            width - y
        else
            throw IllegalArgumentException("Sorry, this function is implemented only for 270 degree rotation...")

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

        return if (270 == angle)
            x
        else
            throw IllegalArgumentException("Sorry, this function is implemented only for 270 degree rotation...")

    }

    private fun rotatePoint(point: Point, angle: Int, width: Int, height: Int): Point {
        return Point(
            rotateXCoordinate(point.x, point.y, angle, width, height),
            rotateYCoordinate(point.x, point.y, angle, width, height)
        )
    }

}