# Multi-Object Motion Detection - Updated Guide

## 🎯 **New Features Implemented:**

### ✅ **Multiple Object Detection**
- **Separate rectangles** for each moving object
- **Different colors** for each detected object (Red, Green, Blue, Yellow, Cyan, Magenta)
- **Object numbering** - each rectangle shows "1", "2", "3", etc.
- **Precise bounding boxes** with minimal padding (3 pixels only)

### ✅ **Improved Precision**
- **Contour-based detection** using connected components algorithm
- **Smaller scale factor** (2x instead of 3x) for better precision
- **Higher motion threshold** (30) to reduce noise
- **Larger minimum area** (300 pixels) to filter out small movements

### ✅ **Better Algorithm**
- **Flood fill algorithm** to find connected white pixels
- **8-connected neighborhood** for more accurate contour detection
- **Multiple contour processing** - each valid contour becomes a separate object
- **Tight bounding boxes** calculated for each contour individually

## 🔧 **Current Detection Settings:**

```kotlin
motionThreshold = 30        // Higher threshold for cleaner detection
minMotionArea = 300         // Minimum pixels per object
scaleFactor = 2             // Better precision than before
padding = 3                 // Minimal padding around objects
minContourSize = 10         // Minimum contour size
```

## 📱 **How to Test:**

### 1. **Start the App**
- Open "Camera-Masking" application
- Grant camera permissions
- Ensure good lighting

### 2. **Begin Multi-Object Detection**
- Tap "Start Motion Detection" button
- Button turns green and shows "Stop Motion Detection"
- Toast message: "Motion detection started! Move objects in front of camera."

### 3. **Test Multiple Objects**
Try these scenarios:
- **Wave both hands** at different sides of the screen
- **Move two books** simultaneously 
- **Walk while holding an object** - should detect both you and the object
- **Multiple people** moving in front of camera
- **Objects of different sizes** - should get different sized rectangles

### 4. **Visual Feedback**
You should see:
- **Multiple colored rectangles** - one for each moving object
- **Object numbers** (1, 2, 3...) in top-left corner of each rectangle
- **Small crosshairs** at the center of each rectangle
- **Tight fitting** rectangles around each object

## 🔍 **Expected Results:**

### Single Object:
- **One red rectangle** tightly fitted around the moving object
- Rectangle follows the object's movement
- Minimal padding around the actual moving area

### Multiple Objects:
- **Separate colored rectangles** for each object:
  - Object 1: Red
  - Object 2: Green  
  - Object 3: Blue
  - Object 4: Yellow
  - Object 5: Cyan
  - Object 6: Magenta
- Each rectangle independently tracks its object
- Numbers help identify each tracked object

## 🐛 **Troubleshooting:**

### Issue: Still Getting Large Rectangles
**Possible Causes:**
- Objects too close together (algorithm merges them)
- Poor lighting causing noise
- Background movement (shadows, reflections)

**Solutions:**
- Ensure objects are well-separated
- Use solid background
- Improve lighting conditions
- Move objects more distinctly

### Issue: Not Detecting Small Objects
**Current minimum area:** 300 pixels
**To detect smaller objects**, modify in MainActivity.kt:
```kotlin
private var minMotionArea = 150  // Smaller objects
```

### Issue: Too Many False Detections
**Current threshold:** 30
**To reduce noise**, increase threshold:
```kotlin
private var motionThreshold = 40  // Less sensitive
```

### Issue: Objects Not Separated
**Objects merging into one rectangle:**
- Move objects further apart
- Ensure clear space between objects
- Check for shadows connecting objects

## 📊 **Debug Information:**

Check Android logs for:
```bash
adb logcat | grep -E "(Motion|ObjectTracking)"
```

Look for:
- `Starting multi-object detection...`
- `Found X contours, min size: Y`
- `Object 0: size=Z, rect=(x,y,w,h)`
- `Detected N objects`
- `Drawing X rectangles`

## 🔬 **Algorithm Details:**

### Frame Difference:
1. Convert current and previous frames to grayscale
2. Calculate absolute difference between frames
3. Apply threshold to create binary image (white = motion)

### Contour Detection:
1. Find connected white pixels using flood fill
2. Group pixels into contours (objects)
3. Filter contours by minimum size
4. Calculate tight bounding box for each contour

### Multiple Objects:
1. Process each contour separately
2. Create individual rectangle for each valid contour
3. Assign different colors to distinguish objects
4. Display all rectangles simultaneously

## 🎨 **Color Coding:**
- **Red (Object 1)**: First detected object
- **Green (Object 2)**: Second detected object  
- **Blue (Object 3)**: Third detected object
- **Yellow (Object 4)**: Fourth detected object
- **Cyan (Object 5)**: Fifth detected object
- **Magenta (Object 6)**: Sixth detected object
- **Cycles back to Red** for 7th+ objects

## 🚀 **Performance Notes:**
- Processes every 2nd frame for real-time performance
- Uses 2x downscaling for speed while maintaining precision
- Efficient flood fill algorithm for contour detection
- Minimal padding reduces rectangle overlap

This implementation should now provide **precise, separate rectangles for each moving object** just like the original Motion-Detection project!
