ProCamTracker
=============

Introduction
------------
ProCamTracker is a user-friendly computer application to turn a perfectly normal pair of color camera and projector into a system that can track without markers a real world object (currently limited to matte planes), while simultaneously projecting on its surface geometrically corrected video images using the direct image alignment algorithm included in [JavaCV](https://github.com/bytedeco/javacv), an open source library I developed as part of my doctoral research. More information about the algorithm itself can be found in the related CVPR 2010 paper below, which you may cite if you find this software useful. Thank you.

Samuel Audet, Masatoshi Okutomi, and Masayuki Tanaka. Direct Image Alignment of Projector-Camera Systems with Planar Surfaces. The 23rd IEEE Conference on Computer Vision and Pattern Recognition (CVPR 2010). IEEE Computer Society, June 2010. http://www.ok.ctrl.titech.ac.jp/~saudet/publications/cvpr2010.pdf

This method requires a geometrically and color calibrated projector-camera system. To perform the calibration, I recommend [ProCamCalib](http://www.ok.ctrl.titech.ac.jp/~saudet/procamcalib/), a tool I previously released for that purpose.


Downloads
---------
 * ProCamTracker 1.1 binary archive  [procamtracker-1.1-bin.zip](http://search.maven.org/remotecontent?filepath=org/bytedeco/procamtracker/1.1/procamtracker-1.1-bin.zip) (109 MB)
 * ProCamTracker 1.1 source archive  [procamtracker-1.1-src.zip](http://search.maven.org/remotecontent?filepath=org/bytedeco/procamtracker/1.1/procamtracker-1.1-src.zip) (67 KB)

The binary archive contains builds for Linux, Mac OS X, and Windows.


Required Software
-----------------
I wrote ProCamTracker itself in Java and its binary should run on any platform where an implementation of Java SE 7 or newer exists. The binary distribution also contains natively compiled code for Linux, Mac OS X, and Windows, needed by JavaCV.

Please install the following before running ProCamTracker:

 * An implementation of Java SE 7 or newer:
   * OpenJDK  http://openjdk.java.net/install/  or
   * Sun JDK  http://www.oracle.com/technetwork/java/javase/downloads/  or
   * IBM JDK  http://www.ibm.com/developerworks/java/jdk/

As well as the following to enable processing with OpenCL and OpenGL:

 * JOCL and JOGL from JogAmp  http://jogamp.org/

And be aware that ProCamTracker runs _a lot_ faster under the "server" JVM than the "client" JVM, but because of its bigger size, not all distributions of Java come with the server one.

Additionally, for IIDC/DCAM cameras, Microsoft's Kinect stereo camera, or the PS3 Eye:

 * libdc1394 2.1.x or 2.2.x  http://sourceforge.net/projects/libdc1394/files/
 * FlyCapture 2.7.x or 2.8.x  http://www.ptgrey.com/flycapture-sdk
 * libfreenect 0.5.x  https://github.com/OpenKinect/libfreenect
 * CL Eye Platform SDK  http://codelaboratories.com/downloads/


Usage
-----
Under Linux, Mac OS X, and other Unix variants, execute either `procamtracker-nativelook` or `procamtracker-oceanlook`, according to the theme that works best on your system. ("Ocean" being Java's original look and feel.) The equivalent files under Windows are `procamtracker-nativelook.cmd` and `procamtracker-oceanlook.cmd`.

After launch, the user interface that appears allows the user to change settings for the camera, the projector, and the various modules of the tracking algorithm. I describe next a typical usage scenario and will not explain all the settings in detail. The default values of the ones I do not mention should be good enough for most cases, but please feel free to experiment.

1. Camera Settings  
Select a suitable `FrameGrabber` for your system, and fill in either `deviceFile`, `deviceNumber` or `devicePath`, and `format` as appropriate. Place the path to the `calibration.yaml` file created by ProCamCalib in the `parametersFile` field, while the `name` field must correspond to an entry in that file.
2. Projector Settings  
As with the camera settings, fill in the `parametersFile` field, but also confirm that the `screenNumber` corresponds to the one of the projector. 
3. RealityAugmentor/ObjectSettings/VirtualSettings  
Locate an image file (PNG, JPG, etc.) or video file (AVI, MP4, etc.) you would like to project on some planar surface and specify its path in the `projectorImageFile` or `projectorVideoFile` field respectively.

Once you have modified all the desired settings, since the application may crash during the operations described below, please save them in an XML file via the Settings menu.

Now, we are ready to start the tracking algorithm. From the Tracking menu, click on the Start item. If all goes well, a window entitled "Initial Alignment" should appear containing an image captured from your camera. In this window, click four different points that delimit the region of interest on your planar surface that you would like to map to the four corners of the projector image, in this order: upper left, upper right, lower right, and lower left. After the last click, tracking should start within a few seconds. You may then begin to move your planar surface and see if the system can successfully track it.

Feel free to contact me if you have any questions or find any problems with the software! I am sure it is far from perfect...


Source Code
-----------
I make all the source code available on GitHub at https://github.com/bytedeco/procamtracker . You will also need the following to modify and build the application:

 * A C/C++ compiler
 * JavaCPP 1.1  https://github.com/bytedeco/javacpp
 * JavaCV  1.1  https://github.com/bytedeco/javacv
 * OpenCV 3.0.0  http://sourceforge.net/projects/opencvlibrary/files/
 * FFmpeg 2.8.x  http://ffmpeg.org/download.html
 * ARToolKitPlus 2.3.x  https://launchpad.net/artoolkitplus
 * NetBeans 8.0  http://netbeans.org/downloads/
 * Maven 3.x  http://maven.apache.org/download.html

(The icons were shamelessly copied from the source code repository of NetBeans. Also licensed under the GPLv2.)

Please keep me informed of any updates or fixes you make to the code so that I may integrate them into my own version. Thank you!


----
Copyright (C) 2009-2015 Samuel Audet [saudet `at` ok.ctrl.titech.ac.jp](mailto:saudet at ok.ctrl.titech.ac.jp)  
Web site: http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/

Licensed under the GNU General Public License version 2 (GPLv2).  
Please refer to LICENSE.txt or http://www.gnu.org/licenses/ for details.
