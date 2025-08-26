# Motion Detection Troubleshooting Guide

## Issues Fixed in Latest Version:

### 1. **Overlay Position Fixed**
- **Problem**: Red rectangle was stuck in the corner
- **Solution**: Updated coordinate scaling to match camera preview size to overlay size
- **Code Change**: Added proper scaling using camera preview parameters instead of surfaceView dimensions

### 2. **Motion Detection Improvements**
- **More Sensitive**: Reduced motion threshold from 20 to 15
- **Smaller Objects**: Reduced minimum motion area from 100 to 50 pixels
- **Better Processing**: Process every 2nd frame instead of every 3rd for faster response
- **Enhanced Logging**: Added detailed debug logs to track detection process

### 3. **Detection Logic Fixed**
- **Problem**: Tracking callback was checking for `trackingRect != null` but we reset it to null when starting
- **Solution**: Removed the null check so detection starts immediately

## Testing the Motion Detection:

### Step 1: Install and Run
1. The app should be installed on your device now
2. Open the "Camera-Masking" app
3. Grant camera permissions

### Step 2: Start Motion Detection
1. Tap the "Start Motion Detection" button
2. The button should turn green and say "Stop Motion Detection"
3. You should see a toast message: "Motion detection started! Move objects in front of camera."

### Step 3: Test Motion Detection
Try these movements in front of the camera:
- **Wave your hand slowly** across the camera view
- **Move a piece of paper** or book
- **Walk in front of the camera**
- **Move any object** with good contrast against the background

### Step 4: Check Logs (if no detection)
If you have Android Studio or ADB connected:
```bash
adb logcat | grep -E "(Motion|ObjectTracking|PythonMotion|Overlay)"
```

Look for these log messages:
- `Motion detection started`
- `Processing frame #X`
- `Comparing frames for motion detection...`
- `Motion detected: X pixels, area: Y`
- `Drawing rect at: (X, Y, X2, Y2)`

## Common Issues and Solutions:

### Issue 1: No Red Rectangle Appears
**Possible Causes:**
- Motion not significant enough
- Poor lighting conditions
- Camera not focusing properly

**Solutions:**
- Try larger, more obvious movements
- Ensure good lighting
- Move objects closer to camera
- Try objects with high contrast (white paper on dark background)

### Issue 2: Rectangle Still in Wrong Position
**Check logs for:**
- `Preview size: WIDTHxHEIGHT`
- `Overlay size: WIDTHxHEIGHT`
- `Drawing rect at: (X, Y, X2, Y2)`

**If scaling is wrong:**
- The preview and overlay sizes should be logged
- Rectangle coordinates should be reasonable (not 0,0,0,0 or huge numbers)

### Issue 3: App Crashes
**Check logs for:**
- `Error processing frame:`
- `Error in motion detection:`
- Java/Kotlin stack traces

## Python Integration (Advanced):

### Enable Python Motion Detection:
1. Change `usePythonDetection = false` to `true` in MainActivity.kt
2. Install Python and OpenCV on your Android device (requires root or Termux)
3. Copy `motion_detector.py` to device
4. Rebuild and install app

### Python Setup (Optional):
```bash
# On Windows/PC for testing the Python script
pip install opencv-python numpy pillow

# Test the Python script
python motion_detector.py background_subtraction [base64_image_data]
```

## Current Detection Settings:

```kotlin
motionThreshold = 15     // Lower = more sensitive
minMotionArea = 50       // Lower = detect smaller objects
scaleFactor = 3          // Image downscaling for performance
frameCount % 2 == 0      // Process every 2nd frame
```

## Next Steps if Still Not Working:

1. **Check Camera Preview**: Ensure camera preview is working normally
2. **Test with Obvious Motion**: Wave a large white sheet of paper
3. **Check Permissions**: Ensure camera permission is granted
4. **Restart App**: Force close and restart the application
5. **Check Device**: Test on a different Android device if available

## Alternative Solutions:

If native motion detection continues to fail, consider:
1. **OpenCV Android SDK**: More robust computer vision
2. **ML Kit Motion Detection**: Google's ML solution
3. **TensorFlow Lite**: Machine learning-based detection
4. **Camera2 API**: More advanced camera controls

## Code Modifications for More Sensitivity:

If you want even more sensitive detection, modify these values in MainActivity.kt:
```kotlin
private var motionThreshold = 10 // Even more sensitive
private var minMotionArea = 25   // Detect very small objects
```

The current implementation should work for most motion detection scenarios. The key fixes were:
1. Fixed overlay positioning
2. Improved detection sensitivity
3. Added comprehensive logging
4. Fixed callback initialization issue
