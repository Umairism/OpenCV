package com.example.application

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.hardware.Camera
import android.util.Log
import java.io.IOException
import android.widget.Button
import android.app.AlertDialog
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.max
import kotlin.math.min
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import android.widget.ImageView
import android.widget.FrameLayout
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private lateinit var surfaceView: SurfaceView
    private var camera: Camera? = null
    private var currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK
    private var currentRotation = 0
    private var availableResolutions = mutableListOf<Camera.Size>()
    private var currentResolutionIndex = 0
    
    // Zoom and touch related variables
    private var currentZoomLevel = 0
    private var maxZoomLevel = 0
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    
    // Object tracking variables
    private var isTracking = false
    private var trackingRects: List<Rect> = emptyList() // Multiple rectangles for multiple objects
    private var previousFrame: ByteArray? = null // Store YUV data directly for speed
    private var currentBitmap: Bitmap? = null
    private var trackingHandler = Handler(Looper.getMainLooper())
    private var trackingOverlay: View? = null
    private var frameCount = 0
    private var motionThreshold = 25 // Balanced threshold for precision
    private var minMotionArea = 400 // Increased to reduce false detections
    private var lastProcessTime = 0L // For FPS calculation
    private var fpsCounter = 0
    private var lastFpsTime = 0L
    private var currentFps = 0f // Current FPS display
    private var adaptiveThreshold = 25 // Dynamic threshold based on conditions
    private var frameHistory = mutableListOf<Int>() // For adaptive thresholding
    private var paint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    
    // Python integration
    private lateinit var executorService: ExecutorService
    private var usePythonDetection = false // Disabled by default, needs Python setup
    private var previousFrameBase64: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation at startup
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        trackingOverlay = findViewById(R.id.trackingOverlay)
        
        // Initialize touch gestures
        setupTouchGestures()
        
        // Initialize camera controls
        setupCameraControls()
        
        // Initialize Python integration
        executorService = Executors.newSingleThreadExecutor()

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            startCameraPreview()
        }
    }

    // Prevent configuration changes from affecting the app
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Force portrait mode and ignore orientation changes
        Log.d("Orientation", "Configuration change detected - maintaining portrait mode")
        // Ensure we stay in portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun setupCameraControls() {
        val btnSwitchCamera = findViewById<Button>(R.id.btnSwitchCamera)
        val btnRotate = findViewById<Button>(R.id.btnRotate)
        val btnResolution = findViewById<Button>(R.id.btnResolution)
        val btnZoomIn = findViewById<Button>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<Button>(R.id.btnZoomOut)
        val btnObjectTracking = findViewById<Button>(R.id.btnObjectTracking)

        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        btnRotate.setOnClickListener {
            rotateCamera()
        }

        btnResolution.setOnClickListener {
            showResolutionDialog()
        }

        btnZoomIn.setOnClickListener {
            zoomIn()
        }

        btnZoomOut.setOnClickListener {
            zoomOut()
        }

        btnObjectTracking.setOnClickListener {
            toggleObjectTracking()
        }
    }

    private fun setupTouchGestures() {
        // Initialize scale gesture detector for pinch-to-zoom
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(0.1f, min(scaleFactor, 5.0f))
                
                // Convert scale factor to zoom level
                val zoomPercent = ((scaleFactor - 1.0f) * maxZoomLevel).toInt()
                currentZoomLevel = max(0, min(zoomPercent, maxZoomLevel))
                
                setZoomLevel(currentZoomLevel)
                return true
            }
        })

        // Set touch listener for focus and pinch-to-zoom
        surfaceView.setOnTouchListener { _, event ->
            // Handle normal camera gestures (no object selection needed for motion detection)
            scaleGestureDetector.onTouchEvent(event)
            
            // Handle tap-to-focus
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!scaleGestureDetector.isInProgress) {
                        focusOnTouch(event.x, event.y)
                    }
                }
            }
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraPreview()
            } else {
                Toast.makeText(this, "Camera permission is required for preview.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCameraPreview() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                openCamera()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // Restart preview if surface changes
                restartCameraPreview(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                releaseCamera()
            }
        })
    }

    private fun openCamera() {
        try {
            camera = Camera.open(currentCameraId)
            setupCameraParameters()
            camera?.setPreviewDisplay(surfaceView.holder)
            setCameraDisplayOrientation()
            camera?.startPreview()
            Log.d("Camera", "Camera preview started")
        } catch (e: IOException) {
            Log.e("Camera", "Error setting camera preview: ${e.message}")
        } catch (e: RuntimeException) {
            Log.e("Camera", "Camera failed to open: ${e.message}")
        }
    }

    private fun setupCameraParameters() {
        camera?.let { cam ->
            val parameters = cam.parameters
            
            // Get available preview sizes for resolution selection
            availableResolutions.clear()
            availableResolutions.addAll(parameters.supportedPreviewSizes)
            
            // OPTIMIZATION: Choose optimal resolution for 60FPS
            // Prefer smaller resolutions for higher frame rates (like OpenCV does)
            val optimalSize = findOptimalPreviewSize(availableResolutions)
            parameters.setPreviewSize(optimalSize.width, optimalSize.height)
            currentResolutionIndex = availableResolutions.indexOf(optimalSize)
            
            Log.d("Camera", "Optimized preview size: ${optimalSize.width}x${optimalSize.height} for high FPS")
            
            // OPTIMIZATION: Set high frame rate if supported
            try {
                val supportedFpsRanges = parameters.supportedPreviewFpsRange
                var bestFpsRange: IntArray? = null
                var maxFps = 0
                
                for (fpsRange in supportedFpsRanges) {
                    val minFps = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                    val maxFpsCandidate = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                    
                    Log.d("Camera", "Supported FPS range: ${minFps/1000}-${maxFpsCandidate/1000}")
                    
                    // Look for 60fps or highest available
                    if (maxFpsCandidate >= 60000 || maxFpsCandidate > maxFps) {
                        maxFps = maxFpsCandidate
                        bestFpsRange = fpsRange
                    }
                }
                
                bestFpsRange?.let { fpsRange ->
                    parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1])
                    Log.d("Camera", "Set FPS range: ${fpsRange[0]/1000}-${fpsRange[1]/1000}")
                }
            } catch (e: Exception) {
                Log.e("Camera", "Error setting FPS: ${e.message}")
            }
            
            // Setup zoom parameters
            if (parameters.isZoomSupported) {
                maxZoomLevel = parameters.maxZoom
                currentZoomLevel = 0
                parameters.zoom = currentZoomLevel
            }
            
            // OPTIMIZATION: Use continuous autofocus for better performance
            if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                Log.d("Camera", "Using continuous video focus for better performance")
            } else if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }
            
            cam.parameters = parameters
        }
    }
    
    // Find optimal preview size for MOBILE 1080p SCREEN
    private fun findOptimalPreviewSize(sizes: List<Camera.Size>): Camera.Size {
        // PRIORITY: 1080p for mobile screen dimensions
        val targets = listOf(
            Pair(1920, 1080), // 1080p - Priority for mobile screen
            Pair(1280, 720),  // 720p - Fallback
            Pair(800, 600),   // SVGA - Second fallback
            Pair(640, 480)    // VGA - Last resort
        )
        
        for ((targetWidth, targetHeight) in targets) {
            var optimalSize = sizes.first()
            var minDiff = Int.MAX_VALUE
            
            for (size in sizes) {
                val diff = kotlin.math.abs(size.width - targetWidth) + kotlin.math.abs(size.height - targetHeight)
                
                if (diff < minDiff) {
                    optimalSize = size
                    minDiff = diff
                }
            }
            
            // If we found a reasonable match for this target, use it
            if (minDiff < 500) { // Close enough threshold
                Log.d("Camera", "Selected size: ${optimalSize.width}x${optimalSize.height} (target: ${targetWidth}x${targetHeight})")
                return optimalSize
            }
        }
        
        // Fallback to first available size
        Log.d("Camera", "Using fallback size: ${sizes.first().width}x${sizes.first().height}")
        return sizes.first()
    }

    // Zoom methods
    private fun zoomIn() {
        if (maxZoomLevel > 0 && currentZoomLevel < maxZoomLevel) {
            currentZoomLevel = min(currentZoomLevel + 5, maxZoomLevel)
            setZoomLevel(currentZoomLevel)
        }
    }

    private fun zoomOut() {
        if (maxZoomLevel > 0 && currentZoomLevel > 0) {
            currentZoomLevel = max(currentZoomLevel - 5, 0)
            setZoomLevel(currentZoomLevel)
        }
    }

    private fun setZoomLevel(zoomLevel: Int) {
        camera?.let { cam ->
            val parameters = cam.parameters
            if (parameters.isZoomSupported) {
                parameters.zoom = zoomLevel
                cam.parameters = parameters
            }
        }
    }

    // Focus methods
    private fun focusOnTouch(x: Float, y: Float) {
        camera?.let { cam ->
            try {
                val parameters = cam.parameters
                if (parameters.maxNumFocusAreas > 0) {
                    val focusAreas = mutableListOf<Camera.Area>()
                    
                    // Convert touch coordinates to camera coordinates (-1000 to 1000)
                    val rect = android.graphics.Rect()
                    val centerX = ((x / surfaceView.width) * 2000 - 1000).toInt()
                    val centerY = ((y / surfaceView.height) * 2000 - 1000).toInt()
                    
                    rect.left = max(centerX - 100, -1000)
                    rect.top = max(centerY - 100, -1000)
                    rect.right = min(centerX + 100, 1000)
                    rect.bottom = min(centerY + 100, 1000)
                    
                    focusAreas.add(Camera.Area(rect, 1000))
                    parameters.focusAreas = focusAreas
                    
                    cam.parameters = parameters
                    cam.autoFocus { success, _ ->
                        if (success) {
                            Log.d("Camera", "Auto focus successful")
                        } else {
                            Log.d("Camera", "Auto focus failed")
                        }
                    }
                } else {

                }
            } catch (e: Exception) {
                Log.e("Camera", "Focus error: ${e.message}")
            }
        }
    }

    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(currentCameraId, info)
        
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            android.view.Surface.ROTATION_0 -> degrees = 0
            android.view.Surface.ROTATION_90 -> degrees = 90
            android.view.Surface.ROTATION_180 -> degrees = 180
            android.view.Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360  // compensate for mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        
        // Add custom rotation
        result = (result + currentRotation) % 360
        
        camera?.setDisplayOrientation(result)
    }

    private fun switchCamera() {
        releaseCamera()
        
        currentCameraId = if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }
        
        openCamera()
    }

    private fun rotateCamera() {
        currentRotation = (currentRotation + 90) % 360
        setCameraDisplayOrientation()
    }

    private fun showResolutionDialog() {
        if (availableResolutions.isEmpty()) return
        
        val resolutionStrings = availableResolutions.map { "${it.width} x ${it.height}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select Resolution")
            .setSingleChoiceItems(resolutionStrings, currentResolutionIndex) { dialog, which ->
                currentResolutionIndex = which
                restartCameraWithNewResolution()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartCameraWithNewResolution() {
        camera?.stopPreview()
        setupCameraParameters()
        camera?.startPreview()
    }

    private fun restartCameraPreview(holder: SurfaceHolder) {
        camera?.stopPreview()
        try {
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
        } catch (e: IOException) {
            Log.e("Camera", "Error restarting camera preview: ${e.message}")
        }
    }

    private fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    // Object tracking methods
    private fun toggleObjectTracking() {
        if (isTracking) {
            stopObjectTracking()
        } else {
            startObjectTracking()
        }
    }

    private fun startObjectTracking() {
        if (camera == null) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            return
        }

        isTracking = true
        trackingRects = emptyList() // Reset tracking rectangles
        frameCount = 0
        
        val btnObjectTracking = findViewById<Button>(R.id.btnObjectTracking)
        btnObjectTracking.text = "Stop Motion Detection"
        btnObjectTracking.setBackgroundColor(Color.GREEN)

        Toast.makeText(this, "Motion detection started! Move objects in front of camera.", Toast.LENGTH_LONG).show()
        
        // Start continuous motion detection
        startContinuousTracking()
        
        Log.d("ObjectTracking", "Motion detection started")
    }

    private fun stopObjectTracking() {
        isTracking = false
        trackingRects = emptyList() // Clear all rectangles
        previousFrame = null
        currentBitmap = null
        frameCount = 0 // Reset frame counter

        val btnObjectTracking = findViewById<Button>(R.id.btnObjectTracking)
        btnObjectTracking.text = "Start Motion Detection"
        btnObjectTracking.setBackgroundColor(Color.parseColor("#FF4444"))

        // Stop preview callback for tracking
        camera?.setPreviewCallback(null)
        
        // Clear visual feedback
        clearTrackingOverlay()

        Log.d("ObjectTracking", "Motion detection stopped")
        Toast.makeText(this, "Motion detection stopped", Toast.LENGTH_SHORT).show()
    }

    private fun selectObjectToTrack(x: Int, y: Int) {
        // Manual object selection disabled - using automatic multi-object detection
        Log.d("ObjectTracking", "Manual selection disabled, using automatic detection")
        Toast.makeText(this, "Using automatic multi-object detection", Toast.LENGTH_SHORT).show()
    }

    private fun startContinuousTracking() {
        camera?.setPreviewCallback { data, camera ->
            if (isTracking) {
                processFrameForTracking(data, camera)
            }
        }
    }

    private fun processFrameForTracking(data: ByteArray, camera: Camera) {
        try {
            val frameStartTime = System.currentTimeMillis()
            
            // Process EVERY frame for 60FPS (no skipping)
            frameCount++
            
            // FPS Calculation
            fpsCounter++
            if (frameStartTime - lastFpsTime >= 1000) { // Update every second
                currentFps = fpsCounter.toFloat()
                fpsCounter = 0
                lastFpsTime = frameStartTime
                Log.d("Performance", "Current FPS: $currentFps")
            }
            
            val parameters = camera.parameters
            val width = parameters.previewSize.width
            val height = parameters.previewSize.height
            
            // HIGH PERFORMANCE: Work directly with YUV data (no bitmap conversion)
            if (previousFrame != null && isTracking) {
                if (usePythonDetection) {
                    // For Python detection, still need bitmap
                    val yuvImage = YuvImage(data, parameters.previewFormat, width, height, null)
                    val out = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out) // Lower quality for speed
                    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                    detectMotionWithPython(bitmap)
                } else {
                    // FAST: Direct YUV processing (like OpenCV)
                    val motionRects = detectMotionFromYUV(data, width, height)
                    if (motionRects.isNotEmpty()) {
                        trackingRects = motionRects
                        updateTrackingOverlay()
                        Log.d("ObjectTracking", "High-speed detection: ${motionRects.size} objects in ${System.currentTimeMillis() - frameStartTime}ms")
                    } else {
                        trackingRects = emptyList()
                    }
                }
            }

            // Store current frame data for next comparison (much faster than bitmap)
            previousFrame = data.clone()
            lastProcessTime = frameStartTime

        } catch (e: Exception) {
            Log.e("ObjectTracking", "Error processing frame: ${e.message}")
        }
    }

    // HIGH PERFORMANCE YUV Motion Detection - OPTIMIZED FOR SPEED
    private fun detectMotionFromYUV(currentData: ByteArray, width: Int, height: Int): List<Rect> {
        try {
            val startTime = System.currentTimeMillis()
            
            val previousData = previousFrame ?: return emptyList()
            
            // ADAPTIVE: Scale factor based on resolution for 1080p optimization
            val scaleFactor = if (width >= 1920 || height >= 1080) 6 else 4 // Higher scale for 1080p
            val smallWidth = width / scaleFactor
            val smallHeight = height / scaleFactor
            val ySize = smallWidth * smallHeight
            
            Log.d("Motion", "1080P MODE: Processing ${smallWidth}x${smallHeight} (scale: $scaleFactor, input: ${width}x${height})")
            
            // Debug: Check if we have valid data
            if (currentData.size != previousData.size) {
                Log.e("Motion", "Frame size mismatch: current=${currentData.size}, previous=${previousData.size}")
                return emptyList()
            }
            
            // Create motion mask with noise reduction
            val motionMask = ByteArray(ySize)
            var motionPixels = 0
            
            // MUCH MORE SENSITIVE: Very low threshold but need to reduce noise
            val speedThreshold = 20 // Increased from 10 to reduce false positives
            
            // Single pass: fast motion detection with high threshold
            for (y in 0 until smallHeight) {
                for (x in 0 until smallWidth) {
                    val scaledX = x * scaleFactor
                    val scaledY = y * scaleFactor
                    val index = scaledY * width + scaledX
                    
                    if (index < currentData.size && index < previousData.size) {
                        val currentY = currentData[index].toInt() and 0xFF
                        val previousY = previousData[index].toInt() and 0xFF
                        val diff = kotlin.math.abs(currentY - previousY)
                        
                        val maskIndex = y * smallWidth + x
                        if (diff > speedThreshold) {
                            motionMask[maskIndex] = 1
                            motionPixels++
                        } else {
                            motionMask[maskIndex] = 0
                        }
                    }
                }
            }
            
            Log.d("Motion", "BALANCED: Motion pixels detected: $motionPixels (threshold: $speedThreshold)")
            
            // Debug: Early exit if no motion
            if (motionPixels < 10) {
                Log.d("Motion", "Not enough motion pixels ($motionPixels), skipping contour detection")
                return emptyList()
            }
            
            // NOISE REDUCTION: Apply morphological operations to clean up detection
            val cleanedMask = applyMorphology(motionMask, smallWidth, smallHeight)
            
            // NOISE REDUCTION: Better contour detection with cleaned mask
            val contours = findContoursFromMask(cleanedMask, smallWidth, smallHeight)
            
            // Convert to precise rectangles - LIMIT TO MAX 3 OBJECTS
            val detectedRects = mutableListOf<Rect>()
            val minContourSize = minMotionArea / (scaleFactor * scaleFactor)
            val maxObjects = 3 // REDUCED: Limit to 3 objects maximum
            
            for (contour in contours) {
                if (detectedRects.size >= maxObjects) break // Stop when we have enough objects
                
                if (contour.size >= minContourSize) {
                    var minX = smallWidth
                    var minY = smallHeight
                    var maxX = 0
                    var maxY = 0
                    
                    for (point in contour) {
                        val x = point % smallWidth
                        val y = point / smallWidth
                        minX = minOf(minX, x)
                        minY = minOf(minY, y)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                    
                    if (maxX > minX && maxY > minY) {
                        // SPEED: Scale back with minimal padding
                        val padding = scaleFactor // Use scale factor as padding
                        val rect = Rect(
                            maxOf(0, (minX * scaleFactor) - padding),
                            maxOf(0, (minY * scaleFactor) - padding),
                            minOf(width, (maxX * scaleFactor) + padding),
                            minOf(height, (maxY * scaleFactor) + padding)
                        )
                        detectedRects.add(rect)
                    }
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d("Motion", "Precise detection: ${detectedRects.size} objects in ${totalTime}ms")
            
            return detectedRects
            
        } catch (e: Exception) {
            Log.e("Motion", "Error in YUV motion detection: ${e.message}")
            return emptyList()
        }
    }
    
    // Apply morphological operations to clean up noise
    private fun applyMorphology(mask: ByteArray, width: Int, height: Int): ByteArray {
        val cleaned = ByteArray(mask.size)
        
        // Erosion followed by dilation (opening) to remove noise
        val temp = ByteArray(mask.size)
        
        // Erosion (remove small noise)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val neighbors = listOf(
                    mask[index - width - 1], mask[index - width], mask[index - width + 1],
                    mask[index - 1], mask[index], mask[index + 1],
                    mask[index + width - 1], mask[index + width], mask[index + width + 1]
                )
                
                // Keep pixel only if all neighbors are motion
                temp[index] = if (neighbors.all { it == 1.toByte() }) 1 else 0
            }
        }
        
        // Dilation (restore size of remaining objects)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val neighbors = listOf(
                    temp[index - width - 1], temp[index - width], temp[index - width + 1],
                    temp[index - 1], temp[index], temp[index + 1],
                    temp[index + width - 1], temp[index + width], temp[index + width + 1]
                )
                
                // Set pixel if any neighbor is motion
                cleaned[index] = if (neighbors.any { it == 1.toByte() }) 1 else 0
            }
        }
        
        return cleaned
    }
    
    // Ultra-fast contour detection using connected components on binary mask
    private fun findContoursFromMask(mask: ByteArray, width: Int, height: Int): List<List<Int>> {
        val visited = BooleanArray(mask.size)
        val contours = mutableListOf<List<Int>>()
        
        for (i in mask.indices) {
            if (!visited[i] && mask[i] == 1.toByte()) {
                val contour = mutableListOf<Int>()
                floodFillMask(mask, visited, i, width, height, contour)
                if (contour.size > 10) { // Minimum contour size
                    contours.add(contour)
                }
            }
        }
        
        return contours
    }
    
    // Optimized flood fill for binary mask - IMPROVED PRECISION
    private fun floodFillMask(mask: ByteArray, visited: BooleanArray, start: Int, width: Int, height: Int, contour: MutableList<Int>) {
        val stack = mutableListOf<Int>()
        stack.add(start)
        
        while (stack.isNotEmpty()) {
            val index = stack.removeAt(stack.size - 1)
            
            if (index < 0 || index >= mask.size || visited[index] || mask[index] != 1.toByte()) {
                continue
            }
            
            visited[index] = true
            contour.add(index)
            
            val x = index % width
            val y = index / width
            
            // PRECISION: Add 8-connected neighbors for better contour detection
            val neighbors = listOf(
                index - width - 1,  // top-left
                index - width,      // top
                index - width + 1,  // top-right
                index - 1,          // left
                index + 1,          // right
                index + width - 1,  // bottom-left
                index + width,      // bottom
                index + width + 1   // bottom-right
            )
            
            for (neighbor in neighbors) {
                val nx = neighbor % width
                val ny = neighbor / width
                
                // Check bounds and ensure we don't wrap around
                if (neighbor >= 0 && neighbor < mask.size && 
                    nx >= 0 && nx < width && ny >= 0 && ny < height &&
                    kotlin.math.abs(nx - x) <= 1 && kotlin.math.abs(ny - y) <= 1) {
                    stack.add(neighbor)
                }
            }
        }
    }
    
    // Simple contour finding algorithm for connected components
    private fun findContours(bitmap: Bitmap, width: Int, height: Int): List<List<Pair<Int, Int>>> {
        val visited = Array(height) { BooleanArray(width) }
        val contours = mutableListOf<List<Pair<Int, Int>>>()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!visited[y][x] && bitmap.getPixel(x, y) == Color.WHITE) {
                    val contour = mutableListOf<Pair<Int, Int>>()
                    floodFill(bitmap, visited, x, y, width, height, contour)
                    if (contour.size > 10) { // Minimum contour size
                        contours.add(contour)
                    }
                }
            }
        }
        
        return contours
    }
    
    // Flood fill algorithm to find connected white pixels
    private fun floodFill(bitmap: Bitmap, visited: Array<BooleanArray>, startX: Int, startY: Int, 
                         width: Int, height: Int, contour: MutableList<Pair<Int, Int>>) {
        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x] || 
                bitmap.getPixel(x, y) != Color.WHITE) {
                continue
            }
            
            visited[y][x] = true
            contour.add(Pair(x, y))
            
            // Add 8-connected neighbors
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
            stack.add(Pair(x + 1, y + 1))
            stack.add(Pair(x - 1, y - 1))
            stack.add(Pair(x + 1, y - 1))
            stack.add(Pair(x - 1, y + 1))
        }
    }
    
    private fun detectMotionWithPython(currentFrame: Bitmap) {
        executorService.execute {
            try {
                // Convert bitmap to base64
                val outputStream = ByteArrayOutputStream()
                currentFrame.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val currentFrameBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                
                val result = if (previousFrameBase64 != null) {
                    // Use frame difference method
                    callPythonScript("frame_difference", previousFrameBase64!!, currentFrameBase64)
                } else {
                    // Use background subtraction method for first frame
                    callPythonScript("background_subtraction", currentFrameBase64)
                }
                
                previousFrameBase64 = currentFrameBase64
                
                // Parse result and update UI
                if (result.isNotEmpty()) {
                    parsePythonResult(result)
                }
                
            } catch (e: Exception) {
                Log.e("PythonMotion", "Error in Python motion detection: ${e.message}")
            }
        }
    }
    
    private fun callPythonScript(method: String, vararg frames: String): String {
        return try {
            val command = mutableListOf("python", "motion_detector.py", method)
            command.addAll(frames)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(java.io.File("/data/data/com.example.application/files/"))
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText()
            
            process.waitFor()
            result
            
        } catch (e: Exception) {
            Log.e("PythonMotion", "Error calling Python script: ${e.message}")
            ""
        }
    }
    
    private fun parsePythonResult(jsonResult: String) {
        try {
            val json = JSONObject(jsonResult)
            val motionDetected = json.getBoolean("motion_detected")
            
            if (motionDetected && json.has("motion_rect")) {
                val motionRect = json.getJSONObject("motion_rect")
                val x = motionRect.getInt("x")
                val y = motionRect.getInt("y")
                val width = motionRect.getInt("width")
                val height = motionRect.getInt("height")
                val area = motionRect.getInt("area")
                
                runOnUiThread {
                    val rect = Rect(x, y, x + width, y + height)
                    trackingRects = listOf(rect) // Convert single rect to list
                    updateTrackingOverlay()
                    Log.d("PythonMotion", "Motion detected: area=$area at ($x,$y) size=${width}x${height}")
                }
            } else {
                Log.d("PythonMotion", "No motion detected by Python")
            }
            
        } catch (e: Exception) {
            Log.e("PythonMotion", "Error parsing Python result: ${e.message}")
        }
    }

    // Keep the old method for compatibility but unused now
    private fun findObjectInNextFrame(currentFrame: Bitmap, lastRect: Rect): Rect? {
        // This method is replaced by motion detection
        return lastRect
    }

    // Advanced tracking method that could be implemented later
    private fun analyzeImageChange(currentFrame: Bitmap, lastFrame: Bitmap?, trackRect: Rect): Boolean {
        // This method could be used to detect if there's significant change in the tracked area
        // For now, just return true to indicate the object is still being tracked
        return true
    }

    private fun updateTrackingOverlay() {
        runOnUiThread {
            trackingOverlay?.let { overlay ->
                overlay.setBackgroundDrawable(object : android.graphics.drawable.Drawable() {
                    override fun draw(canvas: Canvas) {
                        if (trackingRects.isNotEmpty()) {
                            // Get actual camera parameters with 1080p defaults
                            val parameters = camera?.parameters
                            val cameraWidth = parameters?.previewSize?.width?.toFloat() ?: 1920f
                            val cameraHeight = parameters?.previewSize?.height?.toFloat() ?: 1080f
                            
                            // DIRECT APPROACH: Use camera dimensions as-is for testing
                            val yuvWidth = cameraWidth / 6f  // Using scale factor 6 for 1080p
                            val yuvHeight = cameraHeight / 6f
                            
                            // Get overlay dimensions
                            val overlayWidth = canvas.width.toFloat()
                            val overlayHeight = canvas.height.toFloat()
                            
                            // Calculate scaling from YUV space to overlay space
                            val scaleX = overlayWidth / yuvWidth
                            val scaleY = overlayHeight / yuvHeight
                            
                            Log.d("Overlay", "DIRECT TEST - Drawing ${trackingRects.size} rectangles")
                            Log.d("Overlay", "Camera: ${cameraWidth}x${cameraHeight}")
                            Log.d("Overlay", "YUV: ${yuvWidth}x${yuvHeight}")
                            Log.d("Overlay", "Overlay: ${overlayWidth}x${overlayHeight}")
                            Log.d("Overlay", "Scale: X=$scaleX, Y=$scaleY")
                            
                            // Display FPS and resolution info
                            paint.color = Color.WHITE
                            paint.style = Paint.Style.FILL
                            paint.textSize = 50f
                            paint.strokeWidth = 2f
                            paint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                            canvas.drawText("FPS: ${currentFps.toInt()}", 20f, 60f, paint)
                            canvas.drawText("${cameraWidth.toInt()}x${cameraHeight.toInt()}", 20f, 120f, paint)
                            canvas.drawText("Objects: ${trackingRects.size}", 20f, 180f, paint)
                            
                            // DEBUG: Show detection status
                            if (trackingRects.isNotEmpty()) {
                                paint.color = Color.YELLOW
                                canvas.drawText("DETECTING!", 20f, 240f, paint)
                            }
                            
                            // Draw each detected object with different colors
                            val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
                            
                            for ((index, rect) in trackingRects.withIndex()) {
                                // DEBUG: Check original rect values
                                Log.d("Debug", "Original rect: $rect (left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom})")
                                Log.d("Debug", "YUV dimensions: ${yuvWidth}x${yuvHeight}, Canvas: ${overlayWidth}x${overlayHeight}")
                                Log.d("Debug", "Scale factors: X=$scaleX, Y=$scaleY")
                                
                                // ATTEMPT MULTIPLE COORDINATE MAPPINGS TO FIND THE RIGHT ONE
                                
                                // Method 1: Direct scaling
                                val method1Left = rect.left * scaleX
                                val method1Top = rect.top * scaleY
                                val method1Right = rect.right * scaleX
                                val method1Bottom = rect.bottom * scaleY
                                
                                // Method 2: Flipped X
                                val method2Left = (yuvWidth - rect.right) * scaleX
                                val method2Top = rect.top * scaleY
                                val method2Right = (yuvWidth - rect.left) * scaleX
                                val method2Bottom = rect.bottom * scaleY
                                
                                // Method 3: Flipped Y
                                val method3Left = rect.left * scaleX
                                val method3Top = (yuvHeight - rect.bottom) * scaleY
                                val method3Right = rect.right * scaleX
                                val method3Bottom = (yuvHeight - rect.top) * scaleY
                                
                                // Method 4: Both flipped
                                val method4Left = (yuvWidth - rect.right) * scaleX
                                val method4Top = (yuvHeight - rect.bottom) * scaleY
                                val method4Right = (yuvWidth - rect.left) * scaleX
                                val method4Bottom = (yuvHeight - rect.top) * scaleY
                                
                                paint.strokeWidth = 6f
                                paint.style = Paint.Style.STROKE
                                
                                // Draw all 4 methods in different colors
                                val methods = listOf(
                                    Pair("M1", listOf(method1Left, method1Top, method1Right, method1Bottom)),
                                    Pair("M2", listOf(method2Left, method2Top, method2Right, method2Bottom)),
                                    Pair("M3", listOf(method3Left, method3Top, method3Right, method3Bottom)),
                                    Pair("M4", listOf(method4Left, method4Top, method4Right, method4Bottom))
                                )
                                
                                val methodColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
                                
                                for ((methodIndex, methodData) in methods.withIndex()) {
                                    val (name, coords) = methodData
                                    val (left, top, right, bottom) = coords
                                    
                                    if (left < right && top < bottom && 
                                        right > 0 && bottom > 0 && 
                                        left < overlayWidth && top < overlayHeight) {
                                        
                                        paint.color = methodColors[methodIndex]
                                        canvas.drawRect(left, top, right, bottom, paint)
                                        
                                        // Label each method
                                        paint.style = Paint.Style.FILL
                                        paint.textSize = 25f
                                        canvas.drawText(name, left + 5, top + 30, paint)
                                        paint.style = Paint.Style.STROKE
                                        
                                        Log.d("Debug", "$name: ($left, $top, $right, $bottom)")
                                    }
                                }
                            }
                            
                            // EMERGENCY FALLBACK: If objects detected but nothing drawn
                            if (trackingRects.isNotEmpty()) {
                                paint.color = Color.WHITE
                                paint.strokeWidth = 8f
                                paint.style = Paint.Style.STROKE
                                // Draw a test rectangle to confirm drawing works
                                canvas.drawRect(100f, 300f, 200f, 400f, paint)
                                paint.style = Paint.Style.FILL
                                paint.textSize = 30f
                                canvas.drawText("TEST", 110f, 350f, paint)
                            }
                        }
                    }

                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                })
            }
        }
    }

    private fun clearTrackingOverlay() {
        runOnUiThread {
            trackingOverlay?.background = null
            trackingRects = emptyList() // Clear all rectangles
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopObjectTracking()
        releaseCamera()
        if (::executorService.isInitialized) {
            executorService.shutdown()
        }
        camera?.release()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
}