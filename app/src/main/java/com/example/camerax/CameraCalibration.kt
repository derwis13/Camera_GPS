package com.example.camerax

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.util.SizeF

class CameraCalibration(cameraManager: CameraManager) {
    private var cameraCharacteristics: CameraCharacteristics =cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[0])
    private var sensor_dimension:SizeF=cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
    private var focal_length:Float=cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]


    fun getSensorDimension(): SizeF {
        return sensor_dimension
    }
    fun getFocalLength(): Float {
        return focal_length
    }
    fun calculateDistanseFromObject(
        objectHeight_mm: Float,
        objectHeight_px: Int,
        imageHeight_px: Int
    ): Float {
        return focal_length * objectHeight_mm * imageHeight_px / (objectHeight_px * (sensor_dimension.width+sensor_dimension.height)/2)
        //return focal_length * objectHeight_mm * imageHeight_px / (objectHeight_px * (sensor_dimension.height))
    }
}