
"""
Motion Detection Security Camera
Author: Muhammad Umair Hakeem
"""

import cv2
import numpy as np
import os
import logging
from datetime import datetime

# Configuration
VIDEO_SOURCE = 0  # 0 for webcam, or provide video file path
MIN_CONTOUR_AREA = 500
RECORDINGS_DIR = "recordings"
LOGS_DIR = "logs"
LOG_FILE = os.path.join(LOGS_DIR, "events.log")

# Setup logging
os.makedirs(RECORDINGS_DIR, exist_ok=True)
os.makedirs(LOGS_DIR, exist_ok=True)
logging.basicConfig(
    filename=LOG_FILE,
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)

def save_motion_event(frame):
    """Save snapshot of detected motion event."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = os.path.join(RECORDINGS_DIR, f"motion_{timestamp}.jpg")
    cv2.imwrite(filename, frame)
    logging.info(f"Motion event saved: {filename}")

def log_event(message):
    """Log an event to the log file."""
    logging.info(message)

def detect_motion():
    """Main motion detection loop."""
    cap = cv2.VideoCapture(VIDEO_SOURCE)
    if not cap.isOpened():
        log_event("Failed to open video source.")
        print("Error: Unable to access video source.")
        return

    ret, frame1 = cap.read()
    if not ret:
        log_event("Failed to grab first frame.")
        print("Error: Unable to read first frame.")
        cap.release()
        return

    frame1_gray = cv2.cvtColor(frame1, cv2.COLOR_BGR2GRAY)
    frame1_gray = cv2.GaussianBlur(frame1_gray, (21, 21), 0)

    while True:
        ret, frame2 = cap.read()
        if not ret:
            log_event("Failed to read next frame. Exiting loop.")
            break

        frame2_gray = cv2.cvtColor(frame2, cv2.COLOR_BGR2GRAY)
        frame2_gray = cv2.GaussianBlur(frame2_gray, (21, 21), 0)

        diff = cv2.absdiff(frame1_gray, frame2_gray)
        thresh = cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)[1]
        thresh = cv2.dilate(thresh, None, iterations=2)

        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        motion_detected = False
        for contour in contours:
            if cv2.contourArea(contour) < MIN_CONTOUR_AREA:
                continue
            (x, y, w, h) = cv2.boundingRect(contour)
            cv2.rectangle(frame2, (x, y), (x + w, y + h), (0, 255, 0), 2)
            cv2.putText(frame2, "Motion Detected", (10, 20),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)
            if not motion_detected:
                save_motion_event(frame2)
                log_event(f"Motion detected at position: x={x}, y={y}, w={w}, h={h}")
                motion_detected = True

        cv2.imshow("Motion Detector", frame2)
        cv2.imshow("Threshold", thresh)

        frame1_gray = frame2_gray

        if cv2.waitKey(30) & 0xFF == 27:  # Press 'Esc' to exit
            log_event("User exited application.")
            break

    cap.release()
    cv2.destroyAllWindows()
    log_event("Application closed.")

if __name__ == "__main__":
    detect_motion()