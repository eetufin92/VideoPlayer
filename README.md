# VideoPlayer

VideoPlayer is a high-performance, feature-rich Android application designed for an immersive and professional video viewing experience. Built with modern Android technologies, it offers advanced playback control, visual tuning, and seamless multitasking.

## 🚀 Key Features

- **Intent-Based Playback**: Open video files, streaming links, or activities directly from other apps or the file system.
- **HW+ Acceleration**: Optimized hardware decoding for smooth high-resolution playback with minimal battery impact.
- **Advanced Playback Controls**:
    - **Ultra-Slow Motion**: Fine-grained speed control down to **0.05x** for detailed frame analysis.
    - **Precise Seeking**: Smooth custom seek bar for navigating to the exact millisecond.
- **Real-Time Visual Tuning**: Adjust **Brightness** and **Contrast** in real-time using high-performance OpenGL-backed shaders.
- **Interactive Gestures**:
    - **Pinch-to-Zoom**: Zoom into details up to **5x**.
    - **Panning**: Drag and move the zoomed video surface to focus on specific areas.
- **Background Audio**: Continue listening to your content even when the app is in the background or the screen is off, powered by `MediaSessionService`.
- **Picture-in-Picture (PiP)**: Seamlessly multitask with a floating video window that automatically activates when navigating away from the app.
- **Modern Material 3 Design**:
    - **Vibrant Aesthetic**: An energetic "Gold & Deep Purple" color scheme.
    - **Edge-to-Edge**: Full utilization of the display for a cinematic feel.
    - **Adaptive Icon**: A sleek, modern icon matching the app's energetic theme.

## 🛠 Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Media Engine**: Jetpack Media3 (ExoPlayer, Session, Effect)
- **Design System**: Material Design 3
- **Architecture**: Service-backed playback for robust background support.

## 📖 Usage

1. **Launch**: Open the app from the launcher.
2. **Open Video**: Since the app focuses on performance and minimalism, it relies on intents. Simply go to your file manager or browser, select a video, and choose **VideoPlayer** to start playback.
3. **Interact**:
    - **Tap**: Show or hide controls.
    - **Pinch**: Zoom in and out.
    - **Drag**: Pan the zoomed video.
    - **Adjust**: Use the settings icon to tune brightness or contrast.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
