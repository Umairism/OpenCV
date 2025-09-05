#!/bin/bash

# Python Motion Detection Setup Script for Android
# This script sets up Python and OpenCV for the Camera-Masking application

echo "Setting up Python Motion Detection for Android Camera App..."

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed. Please install Python 3 first."
    exit 1
fi

# Install required Python packages
echo "Installing required Python packages..."
pip install opencv-python numpy pillow

# Copy the motion detector script to the Android app assets
echo "Setting up motion detector script..."
if [ -f "motion_detector.py" ]; then
    echo "motion_detector.py found and ready to use"
else
    echo "Error: motion_detector.py not found in current directory"
    exit 1
fi

# Create a test script to verify the setup
cat > test_motion_detection.py << 'EOF'
#!/usr/bin/env python3
import cv2
import numpy as np
import sys

def test_opencv():
    """Test if OpenCV is working properly"""
    try:
        # Create a simple test image
        test_image = np.zeros((100, 100, 3), dtype=np.uint8)
        test_image[25:75, 25:75] = [255, 255, 255]
        
        # Test basic OpenCV operations
        gray = cv2.cvtColor(test_image, cv2.COLOR_BGR2GRAY)
        contours, _ = cv2.findContours(gray, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        print(f"OpenCV test successful! Found {len(contours)} contours")
        print(f"OpenCV version: {cv2.__version__}")
        return True
        
    except Exception as e:
        print(f"OpenCV test failed: {e}")
        return False

if __name__ == "__main__":
    if test_opencv():
        print("✅ Python motion detection setup is ready!")
        sys.exit(0)
    else:
        print("❌ Python motion detection setup failed!")
        sys.exit(1)
EOF

# Run the test
echo "Testing Python motion detection setup..."
python3 test_motion_detection.py

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 Python Motion Detection Setup Complete!"
    echo ""
    echo "To enable Python motion detection in your Android app:"
    echo "1. Set 'usePythonDetection = true' in MainActivity.kt"
    echo "2. Copy motion_detector.py to your Android device's app directory"
    echo "3. Ensure Python and required packages are installed on the device"
    echo ""
    echo "Note: For production use, consider using Android's ML Kit or TensorFlow Lite instead of Python"
else
    echo "❌ Setup failed. Please check the error messages above."
fi
