=ProCamTracker=

==Introduction==
ProCamTracker is a user-friendly computer application to turn a perfectly normal pair of color camera and projector into a system that can track without markers a real world object (currently limited to matte planes), while simultaneously projecting on its surface geometrically corrected video images using the direct image alignment algorithm included in JavaCV ( http://code.google.com/p/javacv/ ), an open source library I developed as part of my doctoral research. More information about the algorithm itself can be found in the related CVPR 2010 paper below, which you may cite if you find this software useful. Thank you.

Samuel Audet, Masatoshi Okutomi, and Masayuki Tanaka. Direct Image Alignment of Projector-Camera Systems with Planar Surfaces. The 23rd IEEE Conference on Computer Vision and Pattern Recognition (CVPR 2010). IEEE Computer Society, June 2010. http://www.ok.ctrl.titech.ac.jp/~saudet/publications/cvpr2010.pdf

This method requires a geometrically and color calibrated projector-camera system. To perform the calibration, I recommend the tool I previously released for that purpose, ProCamCalib ( http://www.ok.ctrl.titech.ac.jp/~saudet/procamcalib/ ).


==Required Software==
I wrote the great majority of ProCamTracker in Java and it should run on any platform where an implementation of Java SE 1.6 exists. The binary distribution also contains 32-bit and 64-bit x86 versions of natively compiled code (`cvkernels`) for both Linux and Windows. (If anyone compiles it under Mac OS X or another platform, please send me the binary and I will include it.) Still, additional software is required.

Please install the following before running ProCamTracker:
 * An implementation of Java SE 6
  * OpenJDK 6  http://openjdk.java.net/install/  or
  * Sun JDK 6  http://java.sun.com/javase/downloads/  or
  * IBM JDK 6  http://www.ibm.com/developerworks/java/jdk/  or
  * Java SE 6 for Mac OS X  http://developer.apple.com/java/  etc.
 * OpenCV 1.1pre1, 2.0, or 2.1  http://sourceforge.net/projects/opencvlibrary/files/

*IMPORTANT NOTES*: 
 * ProCamTracker runs _a lot_ faster under the "server" JVM than the "client" JVM, but because of its bigger size, not all distributions of Java come with the server one.
 * The precompiled binaries of OpenCV 2.0 for Windows are incompatible with Sun JDK 6. Please use the ones of OpenCV 2.1.

If you would like to use the `MarkerDetector` module, you will also need to install:
 * ARToolKitPlus 2.1.1c  http://code.google.com/p/javacv/downloads/list

Additionally, for IIDC/DCAM cameras only:
 * libdc1394 2.1.2 (Linux and Mac OS X)  http://sourceforge.net/projects/libdc1394/files/
 * PGR FlyCapture 1 or 2 (Windows only)  http://www.ptgrey.com/products/pgrflycapture/

Further, camera input via FFmpeg is also supported, but needs FFmpeg 0.6 or more recent:
 * Source code  http://ffmpeg.org/download.html
 * Precompiled Windows DLLs  http://ffmpeg.arrozcru.org/autobuilds/


==Usage==
Under Linux, Mac OS X, and other Unix variants, execute either `procamtracker-nativelook` or `procamtracker-oceanlook`, according to the theme you like best. ("Ocean" being Java's original look and feel.) The equivalent files under Windows are `procamcalib-nativelook.cmd` and `procamcalib-oceanlook.cmd`.

After launch, the user interface that appears allows the user to change settings for the camera, the projector, and the various modules of the tracking algorithm. I describe next a typical usage scenario and will not explain all the settings in details. The default values of the ones I do not mention should be good enough for most cases, but please feel free to experiment.

1. Camera Settings
Select a suitable `FrameGrabber` for your system, and fill in either `deviceFile`, `deviceNumber`, or `devicePath` as appropriate. Place the path to the `calibration.yaml` file created by ProCamCalib in the `parametersFile` field, while the `name` field must correspond to an entry in that file. Finally, the value of `nominalDistance` will be used as initial depth for the initialization algorithm of the image aligner.
2. Projector Settings
As with the camera settings, fill in the `parametersFile` field, but also confirm that the `screenNumber` corresponds to the one of the projector. 
3. TrackingWorker
Locate an image file (PNG, JPG, etc.) or video file (AVI, MP4, etc.) you would like to project on some planar surface and specify its path in the `projectorImageFile` or `projectorVideoFile` field respectively.

Once you have modified all the desired settings, since the application may crash during the operations described below, please save them in an XML file via the Settings menu.

Now, we are ready to start the tracking algorithm. From the Tracking menu, click on the Start item. If all goes well, a window entitled "Initial Alignment" should appear containing an image captured from your camera. In this window, click four different points that delimit the region of interest on your planar surface that you would like to map to the four corners of the projector image, in this order: upper left, upper right, lower right, and lower left. After the last click, tracking should start within a few seconds. You may then begin to move your planar surface and see if the system can successfully track it.

Feel free to contact me if you have any questions or find any problems with the software! I am sure it is far from perfect...


==Source Code==
I make all the source code available at the URL below. It is divided into two parts:
 * JavaCV, which contains wrappers for OpenCV, ARToolKitPlus, libdc1394 2.x, PGR FlyCapture, FFmpeg, and more!
 * ProCamTracker, which implements a user-friendly interface based on JavaCV

In addition to the software above, to modify and build the source code you will need:
 * Whatever native tools needed to build the native `cvkernels` module of JavaCV
 * NetBeans 6.8  http://www.netbeans.org/downloads/
 * Java Native Access 3.2.5  http://jna.dev.java.net/

(The icons were shamelessly copied from the source code repository of NetBeans. Also licensed under the GPLv2.)

Please keep me informed of any updates or fixes you make to the code so that I may integrate them into my own version. Thank you!


==Acknowledgments==
I am currently an active member of the Okutomi & Tanaka Laboratory, Tokyo Institute of Technology, supported by a scholarship from the Ministry of Education, Culture, Sports, Science and Technology (MEXT) of the Japanese Government.


==Changes==
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
Copyright (C) 2009,2010 Samuel Audet <saudet@ok.ctrl.titech.ac.jp>
Web site: http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/

Licensed under the GNU General Public License version 2 (GPLv2).
Please refer to LICENSE.txt or http://www.gnu.org/licenses/ for details.
