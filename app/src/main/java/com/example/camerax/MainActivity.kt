package com.example.camerax

//import android.location.LocationRequest

import SensorEvents
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.camerax.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias MyListener = (luma: Int) -> Unit


public class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var viewBinding: ActivityMainBinding
    var isRecord=false

    private lateinit var dataTextSave:DataTextSave

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var fusedLocationClient:FusedLocationProviderClient

    private lateinit var sensorsEvent:SensorEvents

    private lateinit var sensorManager: SensorManager

    private var mGyroscope: Sensor? = null
    private var mAccelerometer: Sensor? = null

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 300
        fastestInterval = 300
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        maxWaitTime = 500
    }
    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.isNotEmpty()) {
                val location = locationList.last()
//                Toast.makeText(applicationContext, "Latitude: " + location.latitude.toString() + '\n' +
//                        "Longitude: "+ location.longitude, Toast.LENGTH_LONG).show()
//                Log.d("Location d", location.latitude.toString())
//                Log.i("Location i", location.latitude.toString())
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataTextSave= DataTextSave(applicationContext)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //createFile()

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        //sensorsEvent=SensorEvents()
        //Log.d("sensor_d","${sensorsEvent.onCreate(savedInstanceState)}")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        //mAccelerometer=sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)


        // Request camera permissions
        if (allPermissionsGranted()) {
            //writeFileExternalStorage()
            fusedLocationClient?.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper())
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }


        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        event.also {
            var type=it.sensor.stringType
            var value=Triple(it.values[0],it.values[1],it.values[2])
            Log.d("fromSensor","Sensor type: $type, Value: $value")
        }
    }

    override fun onResume() {
        super.onResume()
        mGyroscope?.also { gyroscope ->
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        }
        mAccelerometer?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                fusedLocationClient?.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper())
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        //Log.d("photo_info","${imageCapture.camera!!.camer}")

        // Create time stamped name and MediaStore entry.
//        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
//            .format(System.currentTimeMillis())
        val name=dataTextSave.getNameFile()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                @SuppressLint("MissingPermission")
                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location: Location? ->
                            Log.d("loc_tag", "$location")
                            dataTextSave.writeFileExternalStorage(
                                " ${location!!.latitude.toString()}" +
                                        " ${location!!.longitude.toString()} \n"
                            )
                        }
                }
            }
        )
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
//        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
//            .format(System.currentTimeMillis())
        val name=dataTextSave.getNameFile()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                //Log.d("capture_info", "${}")
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                        isRecord=true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                        isRecord=false
                    }
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("UnsafeOptInUsageError", "MissingPermission")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()


                //Log.d("camera_ifno","${CameraManagerCompat.from(this).unwrap().getCameraCharacteristics("0").get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS).}")


            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(
                    Quality.SD)))
                .build()

            val cameraManager:CameraManager=getSystemService(Context.CAMERA_SERVICE) as CameraManager
            Log.d("camera_info","${CameraCalibration(cameraManager).getSensorDimension()}")
            Log.d("camera_info","${CameraCalibration(cameraManager).calculateDistanseFromObject(2000F,2380,3264)}")

            imageCapture = ImageCapture.Builder().build()
            videoCapture = VideoCapture.withOutput(recorder)

            var frameCounter:Int=0

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MyAnalyzer { it ->
                        //Log.d("Analyzer", "Location: ${fusedLocationClient.lastLocation.result}")
                        //fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,null)
                        if(isRecord) {

                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { location: Location? ->
                                    Log.d("loc_tag", "$location")
                                    dataTextSave.writeFileExternalStorage("$frameCounter ${location!!.latitude.toString()}" +
                                            " ${location!!.longitude.toString()} ${it.toString()}\n")
                                    //dataTextSave.writeFileExternalStorage("${location!!.latitude.toString()}\n")
                                    //  Got last known location. In some rare situations this can be null.
                                }
                            frameCounter++
                        }

                        //if (isRecord) {
                          //  Log.d(TAG, "Degree: ${it}")


                        //}
                    })
                }
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                //cameraProvider.bindToLifecycle(
                 //   this, cameraSelector, preview, imageCapture, imageAnalyzer)
                cameraProvider.bindToLifecycle(this, cameraSelector, preview,imageAnalyzer,imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION, //Manifest.permission.ACCESS_BACKGROUND_LOCATION
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
                //Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    private class MyAnalyzer(private val listener: MyListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            //val luma = pixels.average()

            listener(image.imageInfo.rotationDegrees)

            image.close()
        }
    }
}


