package com.example.test_mlkit_tesseract


//        val src = tessBaseApi!!.thresholdedImage.data
//        val srcBytesArray = src.toTypedArray()
////        var bmp = Bitmap.createBitmap(width, height, Bitmap.Config.HARDWARE);
////        var bmp = Bitmap.createBitmap(bitmap)
////        var buffer = ByteBuffer.wrap(src);
////        buffer.rewind();
////        bmp.copyPixelsFromBuffer(buffer);
//
//        var buffer = ByteBuffer.allocate(src.size)
//        buffer = ByteBuffer.wrap(src)
//        buffer.rewind()
//        var bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        bmp.copyPixelsFromBuffer(buffer);
//
////        var test = BitmapFactory.decodeByteArray(src, 0, src.size);
//
//        var matrix: Array<IntArray> = Array(height) { IntArray(width) }
//        for(i in 0..matrix.size - 1) {
//            var rowArray = matrix[i]
//            for(j in 0..rowArray.size - 1) {
//                val raw_value = tessBaseApi!!.thresholdedImage.getPixel(i, j)
//                rowArray[j] = if (raw_value == -1) 256 else 0
//            }
//            matrix[i] = rowArray
//        }
//
//        var bitmaptest = bitmapFromArray(matrix)
//
//        var test = ""
//        for(row in matrix) {
//            for(j in row) {
//                test += "$j "
//            }
//            test += "\n"
//        }

//    fun bitmapFromArray(pixels2d: Array<IntArray>): Bitmap? {
//        val width = pixels2d.size
//        val height: Int = pixels2d[0].size
//        val pixels = IntArray(width * height)
//        var pixelsIndex = 0
//        for (i in 0 until width) {
//            for (j in 0 until height) {
//                pixels[pixelsIndex] = pixels2d[i][j]
//                pixelsIndex++
//            }
//        }
//        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
//    }