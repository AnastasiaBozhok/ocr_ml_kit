package com.example.test_ml_kit

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.util.*
import kotlin.properties.Delegates

class RecognitionResultAdapter {

    var text_blocks: MutableList<TextBlock> = ArrayList<TextBlock>()

    class TextBlock {
        var recognized_text: String by Delegates.notNull()
        var bounding_box: Rect? = null
        var text_lines: MutableList<TextBase> = ArrayList<TextBase>()

        constructor(mlkit_block:Text.TextBlock) {
            recognized_text = mlkit_block.text
            bounding_box = mlkit_block.boundingBox
            for (line in mlkit_block.lines) {
                text_lines.add(TextBase(line))
            }
        }

        fun getFilteredText(use_filter_flag: Boolean): String {
            val line_length_threshold = 2;

            if (!use_filter_flag)
                return this.recognized_text

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
    }

    fun filteredBlockText(): String {
        return getTextToDisplay(true)
    }

    private fun getTextToDisplay(use_filter_flag: Boolean): String {
        var text_to_display = "\n"
        for (i in 0 until this.text_blocks.size) {
            var block = this.text_blocks[i]
            text_to_display += "BLOCK $i (" +
                    "${block.bounding_box?.left}, " +
                    "${block.bounding_box?.bottom}):\n"
            text_to_display += block.getFilteredText(use_filter_flag) + "\n"
        }
        return text_to_display
    }

}