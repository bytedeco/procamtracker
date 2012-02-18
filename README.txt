=ProCamTracker=

==Introduction==
ProCamTracker is a user-friendly computer application to turn a perfectly normal pair of color camera and projector into a system that can track without markers a real world object (currently limited to matte planes), while simultaneously projecting on its surface geometrically corrected video images using the direct image alignment algorithm included in [http://code.google.com/p/javacv/ JavaCV], an open source library I am developing as part of my doctoral research. More information about the algorithm itself can be found in the related CVPR 2010 paper below, which you may cite if you find this software useful. Thank you.

Samuel Audet, Masatoshi Okutomi, and Masayuki Tanaka. Direct Image Alignment of Projector-Camera Systems with Planar Surfaces. The 23rd IEEE Conference on Computer Vision and Pattern Recognition (CVPR 2010). IEEE Computer Society, June 2010. http://www.ok.ctrl.titech.ac.jp/~saudet/publications/cvpr2010.pdf

This method requires a geometrically and color calibrated projector-camera system. To perform the calibration, I recommend the tool I previously released for that purpose, [http://www.ok.ctrl.titech.ac.jp/~saudet/procamcalib/ ProCamCalib].


==Required Software==
I wrote ProCamTracker itself in Java and its binary should run on any platform where an implementation of Java SE 6 or 7 exists. The binary distribution also contains natively compiled code for Linux, Mac OS X, and Windows, needed by JavaCV. Still, additional software is required. (For answers to problems frequently encountered with OpenCV on the Windows platform, please refer to [http://code.google.com/p/javacv/wiki/Windows7AndOpenCV  Common issues with OpenCV under Windows 7].)

Please install the following before running ProCamTracker:
 * An implementation of Java SE 6 or 7
  * OpenJDK  http://openjdk.java.net/install/  or
  * Sun JDK  http://www.oracle.com/technetwork/java/javase/downloads/  or
  * IBM JDK  http://www.ibm.com/developerworks/java/jdk/  or
  * Java SE for Mac OS X  http://developer.apple.com/java/  etc.
 * OpenCV 2.3.1  http://sourceforge.net/projects/opencvlibrary/files/

As well as the following to enable processing with OpenCL and OpenGL:
 * JOCL and JOGL from JogAmp  http://jogamp.org/

And please make sure your Java and OpenCV have the same bitness: *32-bit and 64-bit modules do not mix under any circumstances*. Further, ProCamTracker runs _a lot_ faster under the "server" JVM than the "client" JVM, but because of its bigger size, not all distributions of Java come with the server one.

Additionally, for IIDC/DCAM cameras, Microsoft's Kinect stereo camera, the PS3 Eye, or other cameras supported via FFmpeg:
 * libdc1394 2.1.x (Linux and Mac OS X)  http://sourceforge.net/projects/libdc1394/files/
 * PGR FlyCapture 1.7~2.2 (Windows only)  http://www.ptgrey.com/products/pgrflycapture/
 * OpenKinect  http://openkinect.org/
 * CL Eye Platform SDK  http://codelaboratories.com/downloads/
 * FFmpeg 0.6.x or 0.7.x  http://ffmpeg.org/download.html
  * Precompiled for Windows  http://ffmpeg.zeranoe.com/builds/  Known compatible builds:
   * http://ffmpeg.zeranoe.com/builds/win32/shared/ffmpeg-0.7.1-win32-shared.7z
   * http://ffmpeg.zeranoe.com/builds/win64/shared/ffmpeg-0.7.1-win64-shared.7z


==Usage==
Under Linux, Mac OS X, and other Unix variants, execute either `procamtracker-nativelook` or `procamtracker-oceanlook`, according to the theme that works best on your system. ("Ocean" being Java's original look and feel.) The equivalent files under Windows are `procamtracker-nativelook.cmd` and `procamtracker-oceanlook.cmd`.

After launch, the user interface that appears allows the user to change settings for the camera, the projector, and the various modules of the tracking algorithm. I describe next a typical usage scenario and will not explain all the settings in details. The default values of the ones I do not mention should be good enough for most cases, but please feel free to experiment.

1. Camera Settings
Select a suitable `FrameGrabber` for your system, and fill in either `deviceFile`, `deviceNumber` or `devicePath`, and `format` as appropriate. Place the path to the `calibration.yaml` file created by ProCamCalib in the `parametersFile` field, while the `name` field must correspond to an entry in that file.
2. Projector Settings
As with the camera settings, fill in the `parametersFile` field, but also confirm that the `screenNumber` corresponds to the one of the projector. 
3. RealityAugmentor/ObjectSettings/VirtualSettings
Locate an image file (PNG, JPG, etc.) or video file (AVI, MP4, etc.) you would like to project on some planar surface and specify its path in the `projectorImageFile` or `projectorVideoFile` field respectively.

Once you have modified all the desired settings, since the application may crash during the operations described below, please save them in an XML file via the Settings menu.

Now, we are ready to start the tracking algorithm. From the Tracking menu, click on the Start item. If all goes well, a window entitled "Initial Alignment" should appear containing an image captured from your camera. In this window, click four different points that delimit the region of interest on your planar surface that you would like to map to the four corners of the projector image, in this order: upper left, upper right, lower right, and lower left. After the last click, tracking should start within a few seconds. You may then begin to move your planar surface and see if the system can successfully track it.

Feel free to contact me if you have any questions or find any problems with the software! I am sure it is far from perfect...


==Source Code==
I make all the source code available on my site at http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/ . You will also need the following to modify and build the application:
 * A C/C++ compiler
 * JavaCPP http://code.google.com/p/javacpp/
 * JavaCV  http://code.google.com/p/javacv/
 * ARToolKitPlus 2.1.1t  http://code.google.com/p/javacv/downloads/list
 * NetBeans 6.9  http://netbeans.org/downloads/

(The icons were shamelessly copied from the source code repository of NetBeans. Also licensed under the GPLv2.)

Please keep me informed of any updates or fixes you make to the code so that I may integrate them into my own version. Thank you!


==Acknowledgments==
I am currently an active member of the Okutomi & Tanaka Laboratory, Tokyo Institute of Technology, supported by a scholarship from the Ministry of Education, Culture, Sports, Science and Technology (MEXT) of the Japanese Government.


==Changes==
===February 18, 2012===
 * `TrackingWorker` now fully support tracking with OpenCL and OpenGL acceleration (when `useOpenCL` is checked)
 * New `TrackingWorker.projectorBufferingSize` (in number of frames to buffer) and `proCamPhaseShift` (in milliseconds) settings to compensate for the delay of the projector display, and of the camera capture as well, while still allowing processing at high FPS
 * Accelerated and parallelized execution of `RealityAugmentor`
 * Updated `ObjectFinder` adding `useFLANN` to its `Settings` properties, letting it use FLANN via OpenCV
 * Cleaned up and optimized `HandMouse`
 * Renamed some `Settings` properties here and there to correct typos and reflect better their meanings

===January 8, 2012===
 * Should now have an easier time automatically finding OpenCV libraries inside standard directories such as `/usr/local/lib/`, `/opt/local/lib/`, and `C:\opencv\`, even when they are not part of the system configuration or PATH
 * New `PS3EyeFrameGrabber` from Jiri Masa can now grab images using the SDK from Code Laboratories
 * `TrackingWorker` now supports processing with OpenCL and OpenGL

===October 1, 2011===
 * Fixed `DC1394FrameGrabber` and `FlyCaptureFrameGrabber` to behave as expected with all Bayer/Raw/Mono/RGB/YUV cameras modes (within the limits of libdc1394 and PGR FlyCapture)

===August 21, 2011===
 * Upgraded support to OpenCV 2.3.1
 * `OpenCVFrameGrabber` now detects when CV_CAP_PROP_POS_MSEC is broken and gives up calling `cvGetCaptureProperty()`

===July 5, 2011===
 * Upgraded support to OpenCV 2.3.0
 * Fixed `OpenKinectFrameGrabber` and `FFmpegFrameGrabber`

===June 10, 2011===
 * New `OpenKinectFrameGrabber` to capture from Microsoft's Kinect stereo camera using OpenKinect
 * The Unix scripts now check for a 64-bit JVM in priority

===May 11, 2011===
 * Changed `Marker.getCenter()` back to the centroid, because it has better noise averaging properties and gives in practice more accurate results than the actual center
 * Added hack to `OpenCVFrameGrabber.start()` to wait for `cvRetrieveFrame()` to return something else than `null` under Mac OS X
 * Added to the scripts `-Dapple.awt.fullscreencapturealldisplays=false` Java option required for full-screen support under Mac OS X 
 * Removed from the scripts the default `-Dsun.java2d.opengl=True` Java option, because since NVIDIA Release 260 family of drivers, most video drivers under Linux do not have good OpenGL support anymore
 * `FFmpegFrameGrabber` now works properly on Windows with newer binaries
 * New `VideoInputFrameGrabber` to capture using DirectShow, useful under Windows 7 where OpenCV and FFmpeg can fail to capture using Video for Windows
 * Changed the output of `monitorWindows` slightly to accommodate better the `HandMouse`

===April 7, 2011===
 * Added a `format` property to camera settings, mostly useful for `FFmpegFrameGrabber`, where interesting values include "dv1394", "mjpeg", "video4linux2", "vfwcap", and "x11grab"
 * Added hack to make sure the temporarily extracted library files get properly deleted under Windows
 * Added (rudimentary) outlier detection and modified zero threshold handling 
 * Added new `HandMouse` and `RealityAugmentor` features
 * Fixed `ProjectiveDevice.distort()`, which mistakenly undistorted projector images instead

===February 19, 2011===
 * Upgraded to the latest version of JavaCV based on JavaCPP instead of JNA, featuring better performance
 * Enhanced a few things of the image alignment algorithm
 * Tried to fix image format conversion inside `FlyCaptureFrameGrabber`, but this is going to require more careful debugging

===November 4, 2010===
 * Renamed the package namespace to `com.googlecode.javacv.procamtracker`, which makes more sense now that JavaCV has been well anchored at Google Code for more than a year, piggybacking on the unique and easy-to-remember domain name, but this means you will need to manually edit any old XML `settings.pct` files and rename the namespace of the classes inside
 * `CanvasFrame` now redraws its `Canvas` after the user resizes the `Frame`
 * Added check to `DC1394FrameGrabber` so that a "Failed to initialize libdc1394" does not crash the JVM
 * `FrameGrabber` now selects the default grabber a bit better
 * Made sweeping changes (for the better, but still not finalized) to `GNImageAligner`, `ProjectiveTransformer`, `ProjectiveGainBiasTransformer`, and `ProCamTransformer`...
 * Fixed display issues with the mouse cursor on Windows, and made a few small cosmetic changes
 * Now tries harder to release native memory on time

===July 30, 2010===
 * Fixed crash that would occur in `CanvasFrame` for some video drivers
 * Fixed crash inside the code for direct alignment caused by the ROI getting set outside the image plane
 * Added `deltaScale` and `tryToFixPlane` settings to `GNImageAligner` (the first used as increment, randomly selected forward or backward, for finite difference), which sometimes help to jump over local minima

===May 30, 2010===
 * Fixed speed setting problem with the `FlyCaptureFrameGrabber`

===April 16, 2010===
 * Modified a few things to get better default behavior of gamma correction
 * `Camera.triggerFlushSize` now defaults to 5 (only affects `OpenCVFrameGrabber` and `FFmpegFrameGrabber`)
 * Replaced `LMImageAligner` by `GNImageAligner`, a more appropriate name for Gauss-Newton with `lineSearch`

===April 8, 2010===
 * Added support for OpenCV 2.1

===April 5, 2010===
 * Fixed mouse cursor under Windows when `objectRoiAcquisition == USER`
 * Added new `VirtualBall` visual element
 * Fixed up the `Chronometer` a bit
 * Added `projectorVideoFile` setting, whose images get merged with `projectorImageFile`
 * Some bugs fixed for FFmpeg

===March 21, 2010===
 * Initial release


----
Copyright (C) 2009-2012 Samuel Audet <saudet@ok.ctrl.titech.ac.jp>
Web site: http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/

Licensed under the GNU General Public License version 2 (GPLv2).
Please refer to LICENSE.txt or http://www.gnu.org/licenses/ for details.

