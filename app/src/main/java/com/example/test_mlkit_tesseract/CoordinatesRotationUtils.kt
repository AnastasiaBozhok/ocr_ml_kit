package com.example.test_mlkit_tesseract

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.util.Log

class CoordinatesRotationUtils {

    // TODO: 22/10/2020 (Anastasia)
    // 1) Make it more efficient (without corner --> rectangle conversion)
    // 2) Make it work with all angles (only 0 and 270 now)
    companion object {
        fun adjustRectCoordinatesByAngle(original_bounding_box: Rect?, angle: Int, width: Int, height: Int): Rect? {
            if (null != original_bounding_box) {
                var cornerPoints = getCornersFromRectange(original_bounding_box)
                for (i in 0 until (cornerPoints.size)) {
                    cornerPoints[i] = rotatePoint(cornerPoints[i], angle, width, height)
                }
                return getRectangleFromCorners(cornerPoints)
            } else {
                return original_bounding_box
            }
        }


        fun getCornersFromRectange(rect: Rect): Array<Point> {
            return arrayOf(
                Point(rect.left,rect.top),
                Point(rect.right,rect.top),
                Point(rect.right,rect.bottom),
                Point(rect.left,rect.bottom)
            )
        }

        fun getRectangleFromCorners(cornerPoints: Array<Point>?): Rect {
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

        fun rotateXCoordinate(x: Int, y: Int, angle: Int, width: Int, height: Int): Int {

//        val angle = 360 - angle
//        val center_x = width / 2
//        val center_y = height / 2
//        return round((x - center_x) * cos(angle * Math.PI / 180) -
//                (height - y - center_y) * sin(angle * Math.PI / 180) + center_x).toInt()

            return if (0 == angle) {
                x
            } else if (270 == angle) {
                width - y
            } else {
                Log.e(TAG, "rotateXCoordinate: Sorry, this function is properly " +
                            "implemented only for 270 degree rotation. The coordinate will not be rotated.")
                x
            }
        }

        fun rotateYCoordinate(x: Int, y: Int, angle: Int, width: Int, height: Int): Int {
            // In an image, downwards is positive Y and rightwards is positive X
            // https://stackoverflow.com/questions/6428192/get-new-x-y-coordinates-of-a-point-in-a-rotated-image

//        val angle = 360 - angle
//        val center_x = width / 2
//        val center_y = height / 2
//        return round(
//            -(x - center_x) * sin(angle * Math.PI / 180) -
//                    (height - y - center_y) * cos(angle * Math.PI / 180) + (height - center_y)
//        ).toInt()

            return if (0 == angle) {
                y
            } else if (270 == angle) {
                x
            } else {
                Log.e(TAG, "rotateYCoordinate: Sorry, this function is properly " +
                        "implemented only for 270 degree rotation. The coordinate will not be rotated.")
                y
            }
        }

        fun rotatePoint(point: Point, angle: Int, width: Int, height: Int): Point {
            return Point(
                rotateXCoordinate(point.x, point.y, angle, width, height),
                rotateYCoordinate(point.x, point.y, angle, width, height)
            )
        }

        fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(angle.toFloat())
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }
}