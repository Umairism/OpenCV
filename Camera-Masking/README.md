
# Camera Motion Detection App 🚧 UNDER DEVELOPMENT

**Status: Work in Progress** - Core functionality implemented, coordinate mapping debugging in progress.

## 📱 Overview

An Android camera application that performs real-time multi-object motion detection with high-performance YUV processing. The app can detect and track multiple moving objects simultaneously while maintaining smooth frame rates.

## ✨ Features

### ✅ Implemented Features
- **Multi-Object Motion Detection**: Detects and tracks up to 3 moving objects simultaneously
- **High-Performance YUV Processing**: Optimized for 20-30 FPS performance 
- **Portrait Mode Enforcement**: Prevents orientation switching issues
- **Adaptive Resolution**: Prioritizes 1080p camera resolution with fallback options
- **Color-Coded Object Tracking**: Each detected object gets a unique color rectangle
- **Real-Time Performance Monitoring**: FPS counter and object count display
- **Advanced Motion Filtering**: Noise reduction and minimum area thresholding

### 🔧 In Development
- **Coordinate Transformation**: Currently debugging precise rectangle positioning
- **Touch-to-Rectangle Mapping**: Working to achieve 1:1 accuracy between finger position and drawn rectangles

## 🏗️ Technical Architecture

### Core Components
- **MainActivity.kt**: Main camera and motion detection logic
- **YUV Processing Engine**: Direct luminance comparison for optimal performance
- **Overlay System**: Custom drawing overlay for real-time rectangle rendering
- **Motion Detection Algorithm**: Adaptive thresholding with morphological operations

### Performance Optimizations
- **YUV Direct Processing**: Bypasses RGB conversion for speed
- **Adaptive Scale Factors**: Automatically adjusts processing based on resolution
- **Efficient Memory Management**: Reuses buffers and minimizes allocations
- **Portrait-Only Mode**: Eliminates orientation-related performance drops

## 🛠️ Technical Specifications

### Camera Configuration
- **Primary Resolution**: 1080p (1920x1080) preferred
- **Fallback Resolutions**: 720p, VGA as needed
- **Color Format**: YUV_420_888 for optimal processing
- **Orientation**: Portrait mode enforced via AndroidManifest

### Motion Detection Parameters
- **Threshold**: Adaptive (20 base value)
- **Minimum Object Area**: 400 pixels
- **Maximum Objects**: 3 simultaneous detections
- **Processing Scale**: 6x for 1080p, 4x for lower resolutions

### Performance Metrics
- **Target FPS**: 60 FPS (currently achieving 20-30 FPS)
- **Detection Latency**: Real-time (<50ms)
- **Memory Usage**: Optimized buffer reuse

## 📁 Project Structure

```
Camera-Masking/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/application/
│   │   │   └── MainActivity.kt          # Main application logic
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml    # UI layout with camera preview
│   │   │   └── values/
│   │   │       └── strings.xml          # App strings
│   │   └── AndroidManifest.xml          # App configuration
├── gradle/                              # Gradle wrapper
├── build.gradle                         # Project build configuration
└── README.md                           # This documentation
```

## 🔄 Development Status

### Current Phase: Coordinate Transformation Debugging
The motion detection algorithm successfully identifies moving objects and counts them correctly. However, the coordinate transformation between the YUV processing space and the screen overlay is not yet precise.

**What's Working:**
- ✅ Object detection and counting
- ✅ High-performance YUV processing
- ✅ Portrait mode stability
- ✅ Multi-object tracking
- ✅ Rectangle drawing system

**What's Being Fixed:**
- 🔧 Precise coordinate mapping between detection and display
- 🔧 Rectangle positioning accuracy
- 🔧 Camera-to-screen coordinate transformation

### Debug System
Extensive debugging infrastructure has been implemented including:
- Multiple coordinate transformation methods (M1, M2, M3, M4)
- Real-time coordinate validation
- Visual debugging indicators
- Comprehensive logging system

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 21+
- Physical Android device with camera
- Kotlin support

### Building the Project
```bash
# Clone the repository
git clone https://github.com/Umairism/OpenCV.git
cd OpenCV/Camera-Masking

# Build and install debug version
./gradlew installDebug

# Or build release version
./gradlew assembleRelease
```

### Running the App
1. Install the APK on your Android device
2. Grant camera permissions when prompted
3. Point camera at moving objects (like your finger)
4. Observe real-time motion detection and rectangle tracking

## 🔍 Current Debugging

The app currently shows 4 different coordinate mapping methods (M1-M4) in different colors to identify the correct transformation:
- **M1 (Red)**: Direct scaling
- **M2 (Green)**: X-axis flipped
- **M3 (Blue)**: Y-axis flipped  
- **M4 (Yellow)**: Both axes flipped

## 🎯 Next Steps

1. **Complete Coordinate Transformation**: Identify and implement the correct mapping method
2. **Performance Optimization**: Achieve target 60 FPS performance
3. **UI Enhancement**: Add settings panel for detection parameters
4. **Advanced Features**: 
   - Object velocity tracking
   - Motion prediction
   - Custom detection zones
   - Recording capabilities

## 🤝 Contributing

This project is currently under active development. Feel free to:
- Report issues or bugs
- Suggest performance improvements
- Contribute coordinate transformation solutions
- Test on different devices and screen resolutions

## 📝 Development Notes

### Known Issues
- Coordinate transformation between YUV processing and screen overlay needs calibration
- Rectangle positioning accuracy requires fine-tuning
- Some devices may have different camera orientation behaviors

### Performance Considerations
- YUV processing provides significant performance benefits over RGB
- Portrait mode enforcement prevents orientation-related crashes
- Adaptive scaling maintains performance across different resolutions

## 📄 License

This project is part of the OpenCV learning repository and is intended for educational and development purposes.

---

**⚠️ Note**: This project is actively under development. Some features may not work as expected, and the coordinate mapping system is currently being debugged and refined.

**Last Updated**: August 26, 2025