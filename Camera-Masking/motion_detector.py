#!/usr/bin/env python3
import cv2
import numpy as np
import sys
import json
import base64
from io import BytesIO
from PIL import Image

class MotionDetector:
    def __init__(self, threshold=20, min_area=100):
        self.threshold = threshold
        self.min_area = min_area
        self.background_subtractor = cv2.createBackgroundSubtractorMOG2(detectShadows=True)
        
    def detect_motion_from_base64(self, frame_data):
        """
        Detect motion from base64 encoded image data
        Returns: dict with motion detection results
        """
        try:
            # Decode base64 image
            image_data = base64.b64decode(frame_data)
            image = Image.open(BytesIO(image_data))
            frame = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
            
            # Apply background subtraction
            fg_mask = self.background_subtractor.apply(frame)
            
            # Find contours
            contours, _ = cv2.findContours(fg_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            
            motion_detected = False
            motion_rect = None
            largest_area = 0
            
            # Find the largest moving object
            for contour in contours:
                area = cv2.contourArea(contour)
                if area > self.min_area and area > largest_area:
                    largest_area = area
                    x, y, w, h = cv2.boundingRect(contour)
                    motion_rect = {
                        'x': int(x),
                        'y': int(y),
                        'width': int(w),
                        'height': int(h),
                        'area': int(area)
                    }
                    motion_detected = True
            
            return {
                'motion_detected': motion_detected,
                'motion_rect': motion_rect,
                'total_contours': len(contours),
                'largest_area': largest_area
            }
            
        except Exception as e:
            return {
                'error': str(e),
                'motion_detected': False,
                'motion_rect': None
            }
    
    def detect_motion_simple(self, frame1_data, frame2_data):
        """
        Simple frame difference motion detection
        """
        try:
            # Decode both frames
            image1_data = base64.b64decode(frame1_data)
            image2_data = base64.b64decode(frame2_data)
            
            image1 = Image.open(BytesIO(image1_data))
            image2 = Image.open(BytesIO(image2_data))
            
            frame1 = cv2.cvtColor(np.array(image1), cv2.COLOR_RGB2GRAY)
            frame2 = cv2.cvtColor(np.array(image2), cv2.COLOR_RGB2GRAY)
            
            # Calculate absolute difference
            diff = cv2.absdiff(frame1, frame2)
            
            # Apply threshold
            _, thresh = cv2.threshold(diff, self.threshold, 255, cv2.THRESH_BINARY)
            
            # Find contours
            contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            
            motion_detected = False
            motion_rect = None
            largest_area = 0
            
            # Find the largest moving object
            for contour in contours:
                area = cv2.contourArea(contour)
                if area > self.min_area and area > largest_area:
                    largest_area = area
                    x, y, w, h = cv2.boundingRect(contour)
                    motion_rect = {
                        'x': int(x),
                        'y': int(y),
                        'width': int(w),
                        'height': int(h),
                        'area': int(area)
                    }
                    motion_detected = True
            
            return {
                'motion_detected': motion_detected,
                'motion_rect': motion_rect,
                'total_contours': len(contours),
                'largest_area': largest_area
            }
            
        except Exception as e:
            return {
                'error': str(e),
                'motion_detected': False,
                'motion_rect': None
            }

def main():
    if len(sys.argv) < 3:
        print(json.dumps({'error': 'Usage: python motion_detector.py <method> <frame_data> [frame2_data]'}))
        return
    
    method = sys.argv[1]
    detector = MotionDetector(threshold=15, min_area=50)
    
    if method == "background_subtraction":
        frame_data = sys.argv[2]
        result = detector.detect_motion_from_base64(frame_data)
    elif method == "frame_difference":
        if len(sys.argv) < 4:
            print(json.dumps({'error': 'Frame difference requires two frames'}))
            return
        frame1_data = sys.argv[2]
        frame2_data = sys.argv[3]
        result = detector.detect_motion_simple(frame1_data, frame2_data)
    else:
        result = {'error': 'Unknown method. Use: background_subtraction or frame_difference'}
    
    print(json.dumps(result))

if __name__ == "__main__":
    main()
