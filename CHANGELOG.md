
### July 27, 2014 version 0.9
 * Upgrade support to FFmpeg 2.3.x
 * Remove `platform` property from `pom.xml`, replaced with the `platform.dependency` one in JavaCPP Presets ([javacv issue #10](https://github.com/bytedeco/javacv/issues/10))

### April 28, 2014 version 0.8
 * Move from Google Code to GitHub as main source code repository
 * Upgrade support to OpenCV 2.4.9 and FFmpeg 2.2.x
 * Upgrade to NetBeans 8.0 and work around conflict between `opencv_highgui` and `com.sun.java.swing.plaf.gtk.GTKLookAndFeel`
 * Rename the `com.googlecode.javacv.procamtracker` package to `org.bytedeco.procamtracker`
 * Removed old NetBeans project files that cause a conflict when trying to open as a Maven project (issue javacv:210)

### January 6, 2014 version 0.7
 * Upgraded support to OpenCV 2.4.8 and FFmpeg 2.1.x
 * `VideoInputFrameGrabber` now uses 640x480 as default image size to prevent "videoInput.getPixels() Error: Could not get pixels."

### September 15, 2013 version 0.6
 * Upgraded support to OpenCV 2.4.6.x and FFmpeg 2.0.x
 * Upgraded to NetBeans 7.3.1
 * Upgraded to ARToolKitPlus 2.3.0 (issue javacv:234)
 * Fixed drawing issues with `MarkerDetector.draw()`

### April 7, 2013 version 0.5
 * Upgraded support to OpenCV 2.4.5 and FFmpeg 1.2

### March 3, 2013 version 0.4
 * Upgraded support to OpenCV 2.4.4 and FFmpeg 1.1

### November 4, 2012 version 0.3
 * Upgraded support to OpenCV 2.4.3 and FFmpeg 1.0

### July 21, 2012 version 0.2
 * Upgraded support to OpenCV 2.4.2 and FFmpeg 0.11

### May 27, 2012 version 0.1
 * Started using version numbers, friendly to tools like Maven, and placing packages in a sort of [Maven repository](http://maven2.javacv.googlecode.com/git/)

### May 12, 2012
 * Upgraded support to OpenCV 2.4.0
 * Added `pom.xml` and assembly files for Maven support and changed the directory structure of the source code to match Maven's standard directory layout

### March 29, 2012
 * Added new `RealityAugmentor.ObjectSettings.surfaceHasTexture` property to have ProCamTracker rectify the projector display on textureless surface planes, using either the `FULL_FRAME` or the centered `HALF_FRAME` of the camera image, specified by the `roiAcquisitionMethod` property
 * Renamed a few other `Settings` properties to reflect better their meanings

### February 18, 2012
 * `TrackingWorker` now fully support tracking with OpenCL and OpenGL acceleration (when `useOpenCL` is checked)
 * New `TrackingWorker.projectorBufferingSize` (in number of frames to buffer) and `proCamPhaseShift` (in milliseconds) settings to compensate for the delay of the projector display, and of the camera capture as well, while still allowing processing at high FPS
 * Accelerated and parallelized execution of `RealityAugmentor`
 * Updated `ObjectFinder` adding `useFLANN` to its `Settings` properties, letting it use FLANN via OpenCV
 * Cleaned up and optimized `HandMouse`
 * Renamed some `Settings` properties here and there to correct typos and reflect better their meanings

### January 8, 2012
 * Should now have an easier time automatically finding OpenCV libraries inside standard directories such as `/usr/local/lib/`, `/opt/local/lib/`, and `C:\opencv\`, even when they are not part of the system configuration or PATH
 * New `PS3EyeFrameGrabber` from Jiri Masa can now grab images using the SDK from Code Laboratories
 * `TrackingWorker` now supports processing with OpenCL and OpenGL

### October 1, 2011
 * Fixed `DC1394FrameGrabber` and `FlyCaptureFrameGrabber` to behave as expected with all Bayer/Raw/Mono/RGB/YUV cameras modes (within the limits of libdc1394 and PGR FlyCapture)

### August 21, 2011
 * Upgraded support to OpenCV 2.3.1
 * `OpenCVFrameGrabber` now detects when CV_CAP_PROP_POS_MSEC is broken and gives up calling `cvGetCaptureProperty()`

### July 5, 2011
 * Upgraded support to OpenCV 2.3.0
 * Fixed `OpenKinectFrameGrabber` and `FFmpegFrameGrabber`

### June 10, 2011
 * New `OpenKinectFrameGrabber` to capture from Microsoft's Kinect stereo camera using OpenKinect
 * The Unix scripts now check for a 64-bit JVM in priority

### May 11, 2011
 * Changed `Marker.getCenter()` back to the centroid, because it has better noise averaging properties and gives in practice more accurate results than the actual center
 * Added hack to `OpenCVFrameGrabber.start()` to wait for `cvRetrieveFrame()` to return something else than `null` under Mac OS X
 * Added to the scripts `-Dapple.awt.fullscreencapturealldisplays=false` Java option required for full-screen support under Mac OS X 
 * Removed from the scripts the default `-Dsun.java2d.opengl=True` Java option, because since NVIDIA Release 260 family of drivers, most video drivers under Linux do not have good OpenGL support anymore
 * `FFmpegFrameGrabber` now works properly on Windows with newer binaries
 * New `VideoInputFrameGrabber` to capture using DirectShow, useful under Windows 7 where OpenCV and FFmpeg can fail to capture using Video for Windows
 * Changed the output of `monitorWindows` slightly to accommodate better the `HandMouse`

### April 7, 2011
 * Added a `format` property to camera settings, mostly useful for `FFmpegFrameGrabber`, where interesting values include "dv1394", "mjpeg", "video4linux2", "vfwcap", and "x11grab"
 * Added hack to make sure the temporarily extracted library files get properly deleted under Windows
 * Added (rudimentary) outlier detection and modified zero threshold handling 
 * Added new `HandMouse` and `RealityAugmentor` features
 * Fixed `ProjectiveDevice.distort()`, which mistakenly undistorted projector images instead

### February 19, 2011
 * Upgraded to the latest version of JavaCV based on JavaCPP instead of JNA, featuring better performance
 * Enhanced a few things of the image alignment algorithm
 * Tried to fix image format conversion inside `FlyCaptureFrameGrabber`, but this is going to require more careful debugging

### November 4, 2010
 * Renamed the package namespace to `com.googlecode.javacv.procamtracker`, which makes more sense now that JavaCV has been well anchored at Google Code for more than a year, piggybacking on the unique and easy-to-remember domain name, but this means you will need to manually edit any old XML `settings.pct` files and rename the namespace of the classes inside
 * `CanvasFrame` now redraws its `Canvas` after the user resizes the `Frame`
 * Added check to `DC1394FrameGrabber` so that a "Failed to initialize libdc1394" does not crash the JVM
 * `FrameGrabber` now selects the default grabber a bit better
 * Made sweeping changes (for the better, but still not finalized) to `GNImageAligner`, `ProjectiveTransformer`, `ProjectiveGainBiasTransformer`, and `ProCamTransformer`...
 * Fixed display issues with the mouse cursor on Windows, and made a few small cosmetic changes
 * Now tries harder to release native memory on time

### July 30, 2010
 * Fixed crash that would occur in `CanvasFrame` for some video drivers
 * Fixed crash inside the code for direct alignment caused by the ROI getting set outside the image plane
 * Added `deltaScale` and `tryToFixPlane` settings to `GNImageAligner` (the first used as increment, randomly selected forward or backward, for finite difference), which sometimes help to jump over local minima

### May 30, 2010
 * Fixed speed setting problem with the `FlyCaptureFrameGrabber`

### April 16, 2010
 * Modified a few things to get better default behavior of gamma correction
 * `Camera.triggerFlushSize` now defaults to 5 (only affects `OpenCVFrameGrabber` and `FFmpegFrameGrabber`)
 * Replaced `LMImageAligner` by `GNImageAligner`, a more appropriate name for Gauss-Newton with `lineSearch`

### April 8, 2010
 * Added support for OpenCV 2.1

### April 5, 2010
 * Fixed mouse cursor under Windows when `objectRoiAcquisition == USER`
 * Added new `VirtualBall` visual element
 * Fixed up the `Chronometer` a bit
 * Added `projectorVideoFile` setting, whose images get merged with `projectorImageFile`
 * Some bugs fixed for FFmpeg

### March 21, 2010
 * Initial release


Acknowledgments
---------------
This project was conceived at the [Okutomi & Tanaka Laboratory](http://www.ok.ctrl.titech.ac.jp/), Tokyo Institute of Technology, where I was supported for my doctoral research program by a generous scholarship from the Ministry of Education, Culture, Sports, Science and Technology (MEXT) of the Japanese Government. I extend my gratitude further to all who have reported bugs, donated code, or made suggestions for improvements (details above)!
