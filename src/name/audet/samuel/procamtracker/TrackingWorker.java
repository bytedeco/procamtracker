/*
 * Copyright (C) 2009,2010 Samuel Audet
 *
 * This file is part of ProCamTracker.
 *
 * ProCamTracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * ProCamTracker is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProCamTracker.  If not, see <http://www.gnu.org/licenses/>.
 */

package name.audet.samuel.procamtracker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.SwingWorker;
import name.audet.samuel.javacv.BaseSettings;
import name.audet.samuel.javacv.CameraDevice;
import name.audet.samuel.javacv.CanvasFrame;
import name.audet.samuel.javacv.FFmpegFrameGrabber;
import name.audet.samuel.javacv.FFmpegFrameRecorder;
import name.audet.samuel.javacv.FrameGrabber;
import name.audet.samuel.javacv.FrameGrabber.ColorMode;
import name.audet.samuel.javacv.FrameRecorder;
import name.audet.samuel.javacv.JavaCV;
import name.audet.samuel.javacv.LMImageAligner;
import name.audet.samuel.javacv.MarkedPlane;
import name.audet.samuel.javacv.Marker;
import name.audet.samuel.javacv.MarkerDetector;
import name.audet.samuel.javacv.ObjectFinder;
import name.audet.samuel.javacv.OpenCVFrameGrabber;
import name.audet.samuel.javacv.Parallel;
import name.audet.samuel.javacv.ProCamTransformer;
import name.audet.samuel.javacv.ProjectiveTransformer;
import name.audet.samuel.javacv.ProjectorDevice;
import name.audet.samuel.javacv.ReflectanceInitializer;
import name.audet.samuel.procamtracker.TrackingWorker.Settings.ObjectRoiAcquisition;
import name.audet.samuel.procamtracker.TrackingWorker.Settings.ProjectionType;

import static name.audet.samuel.javacv.jna.cxcore.*;
import static name.audet.samuel.javacv.jna.cv.*;
import static name.audet.samuel.javacv.jna.highgui.*;

/**
 *
 * @author Samuel Audet
 */
public class TrackingWorker extends SwingWorker {

    public static class Settings extends BaseSettings {

        Rectangle chronometerBounds = new Rectangle(0, -50, 150, 50);
        boolean virtualBallEnabled = false;
        int hysteresisPixelCount = 2000;
        int iteratingTimeMax = 120;
        double monitorWindowsScale = 0.5;
        File outputVideoFile = null;
        File objectImageFile = null;
        File projectorImageFile = null;
        File projectorVideoFile = null;
        public static enum ObjectRoiAcquisition {
           USER, OBJECT_FINDER, MARKER_DETECTOR
        }
        ObjectRoiAcquisition objectRoiAcquisition = ObjectRoiAcquisition.USER;
        public static enum ProjectionType {
           TRACKED, FIXED
        }
        ProjectionType projectionType = ProjectionType.TRACKED;


        public Rectangle getChronometerBounds() {
            return chronometerBounds;
        }
        public void setChronometerBounds(Rectangle chronometerBounds) {
            this.chronometerBounds = chronometerBounds;
        }

        public boolean isVirtualBallEnabled() {
            return virtualBallEnabled;
        }
        public void setVirtualBallEnabled(boolean virtualBallEnabled) {
            this.virtualBallEnabled = virtualBallEnabled;
        }

        public int getHysteresisPixelCount() {
            return hysteresisPixelCount;
        }
        public void setHysteresisPixelCount(int hysteresisPixelCount) {
            this.hysteresisPixelCount = hysteresisPixelCount;
        }

        public int getIteratingTimeMax() {
            return iteratingTimeMax;
        }
        public void setIteratingTimeMax(int iteratingTimeMax) {
            this.iteratingTimeMax = iteratingTimeMax;
        }

        public double getMonitorWindowsScale() {
            return monitorWindowsScale;
        }
        public void setMonitorWindowsScale(double monitorWindowsScale) {
            this.monitorWindowsScale = monitorWindowsScale;
        }

        public File getOutputVideoFile() {
            return outputVideoFile;
        }
        public void setOutputVideoFile(File outputVideoFile) {
            this.outputVideoFile = outputVideoFile;
        }
        public String getOutputVideoFilename() {
            return outputVideoFile == null ? "" : outputVideoFile.getPath();
        }
        public void setOutputVideoFilename(String outputVideoFilename) {
            this.outputVideoFile = outputVideoFilename == null ||
                    outputVideoFilename.length() == 0 ? null : new File(outputVideoFilename);
        }

        public File getObjectImageFile() {
            return objectImageFile;
        }
        public void setObjectImageFile(File objectImageFile) {
            this.objectImageFile = objectImageFile;
        }
        public String getObjectImageFilename() {
            return objectImageFile == null ? "" : objectImageFile.getPath();
        }
        public void setObjectImageFilename(String objectImageFilename) {
            this.objectImageFile = objectImageFilename == null ||
                    objectImageFilename.length() == 0 ? null : new File(objectImageFilename);
        }

        public File getProjectorImageFile() {
            return projectorImageFile;
        }
        public void setProjectorImageFile(File projectorImageFile) {
            this.projectorImageFile = projectorImageFile;
        }
        public String getProjectorImageFilename() {
            return projectorImageFile == null ? "" : projectorImageFile.getPath();
        }
        public void setProjectorImageFilename(String projectorImageFilename) {
            this.projectorImageFile = projectorImageFilename == null ||
                    projectorImageFilename.length() == 0 ? null : new File(projectorImageFilename);
        }

        public File getProjectorVideoFile() {
            return projectorVideoFile;
        }
        public void setProjectorVideoFile(File projectorVideoFile) {
            this.projectorVideoFile = projectorVideoFile;
        }
        public String getProjectorVideoFilename() {
            return projectorVideoFile == null ? "" : projectorVideoFile.getPath();
        }
        public void setProjectorVideoFilename(String projectorVideoFilename) {
            this.projectorVideoFile = projectorVideoFilename == null ||
                    projectorVideoFilename.length() == 0 ? null : new File(projectorVideoFilename);
        }

        public ObjectRoiAcquisition getObjectRoiAcquisition() {
            return objectRoiAcquisition;
        }
        public void setObjectRoiAcquisition(ObjectRoiAcquisition objectRoiAcquisition) {
            this.objectRoiAcquisition = objectRoiAcquisition;
        }

        public ProjectionType getProjectionType() {
            return projectionType;
        }
        public void setProjectionType(ProjectionType projectionType) {
            this.projectionType = projectionType;
        }

    }

    CameraDevice   .Settings cameraSettings;
    ProjectorDevice.Settings projectorSettings;
    ObjectFinder   .Settings objectFinderSettings;
    MarkerDetector .Settings markerDetectorSettings;
    LMImageAligner .Settings alignerSettings;
                    Settings trackingSettings;
    VirtualBall    .Settings virtualBallSettings;

    CameraDevice cameraDevice = null;
    FrameGrabber frameGrabber = null;
    ProjectorDevice projectorDevice = null;
    CanvasFrame projectorFrame = null;

    private double[] roiPts = null;
    private ProCamTransformer transformer;
    private ProCamTransformer.Parameters parameters, lastParameters, tempParameters;
    private LMImageAligner aligner;
    private ReflectanceInitializer reflectanceInitializer;
    private MarkerDetector markerDetector;

    private Chronometer chronometer = null;
    private VirtualBall virtualBall = null;

    private FrameGrabber videoToProject;

    private IplImage grabbedImage, undistortedCameraImage, distortedProjectorImage, imageToProject, objectImage;
    private IplImage[] projectorImages, monitorImages;
    private int projectorImageIndex;
    private String[] monitorWindowsTitles = { "Initial Alignment", "Warped Object", "Residual Image", "Camera Target" };
    private CanvasFrame[] monitorWindows = null;

    private ProjectiveTransformer composeWarper = new ProjectiveTransformer();
    private ProjectiveTransformer.Parameters composeParameters = composeWarper.createParameters();
    private CvMat srcPts = CvMat.create(4, 1, CV_64F, 2), dstPts = CvMat.create(4, 1, CV_64F, 2);
    private CvMat tempH  = CvMat.create(3, 3);
    private CvPoint[] temppts = CvPoint.createArray(4), corners = CvPoint.createArray(4);
    private CvRect.ByValue roi = new CvRect.ByValue(), maxroi = new CvRect.ByValue();
    private CvRect.ByValue[] prevroi = { new CvRect.ByValue(0, 0, 0, 0), new CvRect.ByValue(0, 0, 0, 0) };

    private static final Logger logger = Logger.getLogger(TrackingWorker.class.getName());

    public static final int INITIALIZING = 1, TRACKING = 2;

    public boolean cancel() {
        return cancel(getProgress() == INITIALIZING);
    }

    private double[] acquireRoiFromUser() throws Exception {
        final double[] roiPts = new double[8];

        if (monitorWindows == null || monitorWindows[0] == null) {
            throw new Exception("Error: No monitor window. Could not acquire ROI from user.");
        }
        Toolkit t = Toolkit.getDefaultToolkit();
        Dimension d = t.getBestCursorSize(15, 15);
        BufferedImage cursorImage = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cursorImage.createGraphics();
        int cx = d.width/2, cy = d.height/2;
        g.setColor(Color.WHITE); g.drawRect(cx-7, cy-7, 14, 14);
        g.setColor(Color.BLACK); g.drawRect(cx-6, cy-6, 12, 12);
        g.setColor(Color.WHITE); g.drawRect(cx-2, cy-2,  4,  4);
        g.setColor(Color.BLACK); g.drawRect(cx-1, cy-1,  2,  2);
        Cursor cursor = t.createCustomCursor(cursorImage, d.width == 15 && d.height == 15 ?
                    new Point(cx, cy) : new Point(cx+1, cy+1), null);
        monitorWindows[0].setCursor(cursor);

        final int[] count = { 0 };
        monitorWindows[0].getCanvas().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (count[0] < 8) {
                    roiPts[count[0]++] = e.getX()/trackingSettings.getMonitorWindowsScale();
                    roiPts[count[0]++] = e.getY()/trackingSettings.getMonitorWindowsScale();
                    Graphics2D g = monitorWindows[0].acquireGraphics();
                    g.setColor(Color.RED);
                    g.drawLine(e.getX()-7, e.getY(), e.getX()+7, e.getY());
                    g.drawLine(e.getX(), e.getY()-7, e.getX(), e.getY()+7);
                    monitorWindows[0].releaseGraphics(g);
                }
                if (count[0] >= 8) {
                    synchronized (roiPts) {
                        monitorWindows[0].getCanvas().removeMouseListener(this);
                        monitorWindows[0].setCursor(null);
                        roiPts.notify();
                    }
                }
            }
        });

        synchronized (roiPts) {
            roiPts.wait();
        }

        return roiPts;
    }

    private double[] acquireRoiFromObjectFinder(IplImage grabbedImage) throws Exception {
        double[] roiPts = new double[8];

        IplImage grey1, grey2;
        if (objectImage.depth == 1) {
            grey1 = objectImage;
        } else {
            grey1 = IplImage.create(objectImage.width,  objectImage.height,  IPL_DEPTH_8U, 1);
            cvCvtColor(objectImage, grey1, CV_BGR2GRAY);
        }
        if (grabbedImage.depth == 1) {
            grey2 = grabbedImage;
        } else {
            grey2 = IplImage.create(grabbedImage.width,  grabbedImage.height,  IPL_DEPTH_8U, 1);
            cvCvtColor(grabbedImage, grey2, CV_BGR2GRAY);
        }

        objectFinderSettings.setObjectImage(grey1);
        ObjectFinder objectFinder = new ObjectFinder(objectFinderSettings);
        CvPoint2D64f[] pts = objectFinder.find(grey2);
        if (pts == null) {
            return null;
        }
        int i = 0;
        for (CvPoint2D64f p : pts) {
            roiPts[i++] = p.x;
            roiPts[i++] = p.y;
        }
        if (grey1 != objectImage) {
            grey1.release();
        }
        if (grey2 != grabbedImage) {
            grey2.release();
        }
        return roiPts;
    }

    private double[] acquireRoiFromMarkerDetector(IplImage grabbedImage) throws Exception {
        double[] roiPts = new double[8];

        markerDetector = new MarkerDetector(markerDetectorSettings);
        Marker[] markersin = markerDetector.detect(objectImage, false);
        String infoLogString = "objectImage marker centers = ";
        for (int i = 0; i < 4; i++) {
            for (Marker m : markersin) {
                if (m.id == i) {
                    double[] c = m.getCenter();
                    infoLogString += m.id + ": (" + c[0] + ", " + c[1] + ")  ";
                    break;
                }
            }
        }
        logger.info(infoLogString);
        if (markersin == null || markersin.length == 0) {
            throw new Exception("Error: MarkerDetector detected no markers in \"" +
                    trackingSettings.objectImageFile + "\".");
        }
        MarkedPlane markedPlane = new MarkedPlane(objectImage.width, objectImage.height, markersin, 1);

        Marker[] markersout = markerDetector.detect(grabbedImage, false);
        infoLogString = "initial marker centers = ";
        if (markersout == null || markersout.length == 0 ||
                markedPlane.getTotalWarp(markersout, tempH, true) == Double.POSITIVE_INFINITY) {
            throw new Exception("Error: MarkerDetector failed to match markers in the grabbed image.");
        }
        srcPts.put(0.0, 0.0,  objectImage.width, 0.0,
                objectImage.width, objectImage.height,  0.0, objectImage.height);
        cvPerspectiveTransform(srcPts, dstPts, tempH);
        dstPts.get(roiPts);

        for (int i = 0; i < 4; i++) {
            for (Marker m : markersout) {
                if (m.id == i) {
                    double[] c = m.getCenter();
                    infoLogString += m.id + ": (" + c[0] + ", " + c[1] + ")  ";
                    srcPts.put(i*2  , c[0]);
                    srcPts.put(i*2+1, c[1]);
                    break;
                }
            }
        }
        logger.info(infoLogString);

        return roiPts;
    }

    private Runnable doCamera = new Runnable() { public void run() {
        if (trackingSettings.projectionType != ProjectionType.FIXED) {
            transformer.setProjectorImage(projectorImages[(projectorImageIndex+1)%2], alignerSettings.getPyramidLevels());
        }

        try {
            grabbedImage = frameGrabber.grab();
            // gamma "uncorrection", linearization
            double gamma = cameraSettings.getResponseGamma();
            if (gamma != 1.0) {
                grabbedImage.applyGamma(gamma);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (grabbedImage != null) {
            cameraDevice.undistort(grabbedImage, undistortedCameraImage);
            if (aligner != null) {
                aligner.setTargetImage(undistortedCameraImage);
            }
        }
    }};

    private IplImage nextFrameImage() {
        IplImage frameImage = imageToProject;
        if (videoToProject != null) {
            try {
                frameImage = videoToProject.grab();
                if (frameImage == null) {
                    videoToProject.stop();
                    videoToProject.start();
                    frameImage = videoToProject.grab();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (imageToProject != null) {
                // merge images with alpha blending...
                ByteBuffer srcBuf = imageToProject.getByteBuffer();
                ByteBuffer dstBuf = frameImage    .getByteBuffer();
                int srcChannels = imageToProject.nChannels, dstChannels = frameImage.nChannels;
                int srcStep     = imageToProject.widthStep, dstStep     = frameImage.widthStep;
                int width  = Math.min(imageToProject.width,  frameImage.width);
                int height = Math.min(imageToProject.height, frameImage.height);
                IplROI roi = imageToProject.roi;
                if (roi != null) {
                    srcBuf.position(srcStep*roi.yOffset + roi.xOffset*imageToProject.nChannels);
                    dstBuf.position(dstStep*roi.yOffset + roi.xOffset*frameImage    .nChannels);
                    width  = roi.width;
                    height = roi.height;
                }
                int srcLine = srcBuf.position(), dstLine = dstBuf.position();
                for (int y = 0; y < height && srcLine < srcBuf.capacity() &&
                        dstLine < dstBuf.capacity(); y++) {
                    for (int x = 0; x < width; x++) {
                        int a = 128, a2 = 128, b = 0, g = 0, r = 0;
                        switch (srcChannels) {
                            default: assert (false);
                            case 4: a = srcBuf.get()&0xFF; a2 = 255-a;
                            case 3: b = srcBuf.get()&0xFF;
                            case 2: g = srcBuf.get()&0xFF;
                            case 1: r = srcBuf.get()&0xFF;
                        }
                        switch (dstChannels) {
                            default: assert (false);
                            case 4: dstBuf.put((byte)(a+(a2 = dstBuf.get(dstBuf.position())&0xFF)));
                            case 3: dstBuf.put((byte)((b*a + (dstBuf.get(dstBuf.position())&0xFF)*a2)/255));
                            case 2: dstBuf.put((byte)((g*a + (dstBuf.get(dstBuf.position())&0xFF)*a2)/255));
                            case 1: dstBuf.put((byte)((r*a + (dstBuf.get(dstBuf.position())&0xFF)*a2)/255));
                        }
                    }
                    srcBuf.position(srcLine += srcStep);
                    dstBuf.position(dstLine += dstStep);
                }
            }
        }
        if (chronometer != null) {
            chronometer.draw(frameImage);
        }
        return frameImage;
    }

    private Runnable doProjector = new Runnable() { public void run() {
        IplImage frameImage = nextFrameImage();

//        srcPts.put(0.0, 0.0,  frameImage.width, 0.0,
//                frameImage.width, frameImage.height,  0.0, frameImage.height);
//        JavaCV.getPerspectiveTransform(srcPts.get(), roiPts, tempH);
        composeParameters.compose(parameters.getProjectorParameters(), false, parameters.getSurfaceParameters(), false);
        composeParameters.compose(composeParameters.getH(), false, tempH, false);
        composeWarper.transform(srcPts, dstPts, composeParameters, false);
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE,
               minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        for (int i = 0; i < dstPts.getLength(); i++) {
            double x2 = dstPts.get(2*i  );
            double y2 = dstPts.get(2*i+1);
            minX = Math.min(minX, x2);
            minY = Math.min(minY, y2);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }
        // add +3 all around because cvWarpPerspective() needs it apparently
        minX = Math.max(0, minX-3);
        minY = Math.max(0, minY-3);
        maxX = Math.min(projectorImages[projectorImageIndex].width,  maxX+3);
        maxY = Math.min(projectorImages[projectorImageIndex].height, maxY+3);

        // there seems to be something funny with memory alignment and
        // ROIs, so let's align our ROI to a 16 byte boundary just in case..
        roi.x      = (int)Math.floor(minX/16)*16;
        roi.y      = (int)Math.floor(minY);
        roi.width  = (int)Math.ceil (maxX/16)*16 - roi.x;
        roi.height = (int)Math.ceil (maxY)       - roi.y;

        maxroi.x       = Math.min(prevroi[projectorImageIndex].x, roi.x);
        maxroi.y       = Math.min(prevroi[projectorImageIndex].y, roi.y);
        maxroi.width   = Math.max(prevroi[projectorImageIndex].x+prevroi[projectorImageIndex].width,  roi.x+roi.width)  - maxroi.x;
        maxroi.height  = Math.max(prevroi[projectorImageIndex].y+prevroi[projectorImageIndex].height, roi.y+roi.height) - maxroi.y;

        composeWarper.transform(frameImage, projectorImages[projectorImageIndex], maxroi, 0, composeParameters, false);

        prevroi[projectorImageIndex].x      = roi.x;
        prevroi[projectorImageIndex].y      = roi.y;
        prevroi[projectorImageIndex].width  = roi.width;
        prevroi[projectorImageIndex].height = roi.height;

        if (virtualBall != null) {
            cvSetImageROI(projectorImages[projectorImageIndex], roi);
            virtualBall.draw(projectorImages[projectorImageIndex], dstPts.get());
        }

        if (projectorFrame != null) {
            cvResetImageROI(distortedProjectorImage);
            cvResetImageROI(projectorImages[projectorImageIndex]);
            projectorDevice.distort(projectorImages[projectorImageIndex], distortedProjectorImage);
            cvSetImageROI(projectorImages[projectorImageIndex], maxroi);
            cvSetImageROI(distortedProjectorImage, maxroi);
            projectorFrame.showImage(distortedProjectorImage.
                    getBufferedImage(1.0/projectorSettings.getResponseGamma()));
        }
    }};

    public void init() throws Exception {
        // create arrays and canvas frames on the Event Dispatcher Thread...
        if (cameraDevice == null) {
            cameraDevice = new CameraDevice(cameraSettings);
        } else {
            cameraDevice.setSettings(cameraSettings);
        }

        if (projectorDevice == null) {
            projectorDevice = new ProjectorDevice(projectorSettings);
        } else {
            projectorDevice.setSettings(projectorSettings);
        }
        projectorFrame = projectorDevice.createCanvasFrame();

        if (trackingSettings.getMonitorWindowsScale() > 0) {
            monitorWindows = new CanvasFrame[4];
            for (int i = 0; i < monitorWindows.length; i++) {
                monitorWindows[i] = new CanvasFrame("Image Frame " + i);
            }
        } else {
            monitorWindows = null;
        }
    }

    // synchronized with done()...
    @Override protected synchronized Object doInBackground() throws Exception {
        try {
            setProgress(INITIALIZING);

            // perform initialization of camera device
            frameGrabber = cameraDevice.createFrameGrabber();
            frameGrabber.setTriggerMode(true);
            frameGrabber.setColorMode(ColorMode.BGR);
            frameGrabber.start();
            frameGrabber.trigger();
            final IplImage initImage = frameGrabber.grab();

            if (initImage.width != cameraDevice.imageWidth ||
                    initImage.height != cameraDevice.imageHeight) {
                cameraDevice.rescale(initImage.width, initImage.height);
            }

            FrameRecorder frameRecorder = null;
            if (trackingSettings.outputVideoFile != null) {
                frameRecorder = new FFmpegFrameRecorder(trackingSettings.outputVideoFile,
                        initImage.width, initImage.height);
                frameRecorder.start();
            }

            // allocate memory for all images and load video
            undistortedCameraImage  = IplImage.createCompatible(initImage);
            distortedProjectorImage = IplImage.create(projectorDevice.imageWidth,
                    projectorDevice.imageHeight, IPL_DEPTH_8U, initImage.nChannels);
            if (trackingSettings.projectorImageFile == null && trackingSettings.projectorVideoFile == null) {
                // if no image file is given, use our own special fractal as image :)
                imageToProject = IplImage.create(projectorDevice.imageWidth,
                        projectorDevice.imageHeight, IPL_DEPTH_32F, initImage.nChannels);
                projectorDevice.getRectifyingHomography(cameraDevice, tempH);
                JavaCV.fractalTriangleWave(imageToProject, tempH);
            } else if (trackingSettings.projectorVideoFile != null) {
                if (trackingSettings.projectorImageFile != null) {
                    // loads alpha channel
                    imageToProject = IplImage.createFrom(ImageIO.read(trackingSettings.projectorImageFile));
                    if (imageToProject.nChannels == 4) {
                        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                            minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                        ByteBuffer bb = imageToProject.getByteBuffer();
                        for (int y = 0; y < imageToProject.height; y++) {
                            for (int x = 0; x < imageToProject.width; x++) {
                                if (bb.get(y*imageToProject.widthStep + x*imageToProject.nChannels) != 0) {
                                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                                }
                            }
                        }
                        cvSetImageROI(imageToProject, cvRect(minX, minY, maxX-minX, maxY-minY));
                    }
                }
                try {
                    videoToProject = new FFmpegFrameGrabber(trackingSettings.projectorVideoFile);
                } catch (Throwable t) {
                    videoToProject = new OpenCVFrameGrabber(trackingSettings.projectorVideoFile);
                }
                if (videoToProject != null) {
                    videoToProject.setColorMode(ColorMode.BGR);
                    if (imageToProject != null) {
                        videoToProject.setImageWidth (imageToProject.width);
                        videoToProject.setImageHeight(imageToProject.height);
                    }
                    videoToProject.start();
                }
            } else if (trackingSettings.projectorImageFile != null) {
                // does not load alpha channel
                imageToProject = cvLoadImage(trackingSettings.projectorImageFile.getAbsolutePath());
                if (imageToProject == null) {
                    throw new Exception("Error: Could not load projectorImageFile named \"" + trackingSettings.projectorImageFile + "\".");
                }
            }

            projectorImages = new IplImage[]
                { IplImage.createCompatible(distortedProjectorImage),
                  IplImage.createCompatible(distortedProjectorImage) };
            projectorImageIndex = 0;

            // grab the three frames for initialization
            LMImageAligner.Settings s = alignerSettings.clone();
            // remove settings that might reduce accuracy
            s.setZeroThresholds(new double[] { 0.0 });
            s.setErrorDecreaseMin(0);
            reflectanceInitializer = new ReflectanceInitializer(cameraDevice, projectorDevice, initImage.nChannels, s);
            IplImage[] projectorInitFloatImages = reflectanceInitializer.getProjectorImages();
            IplImage[] projectorInitImages   = new IplImage[projectorInitFloatImages.length];
            IplImage[] cameraInitImages      = new IplImage[projectorInitFloatImages.length];
            IplImage[] cameraInitFloatImages = new IplImage[projectorInitFloatImages.length];
            for (int i = 0; i < projectorInitFloatImages.length; i++) {
                projectorInitImages[i]   = IplImage.createCompatible(distortedProjectorImage);
                cameraInitImages[i]      = IplImage.createCompatible(undistortedCameraImage);
                cameraInitFloatImages[i] = IplImage.create(initImage.width, initImage.height, IPL_DEPTH_32F, initImage.nChannels);
                cvConvertScale(projectorInitFloatImages[i], projectorInitImages[i], 255, 0);
                projectorDevice.distort(projectorInitImages[i], distortedProjectorImage);
                // apply gamma correction
                if (projectorSettings.getResponseGamma() != 1.0) {
                    distortedProjectorImage.applyGamma(1.0/projectorSettings.getResponseGamma());
                }
                cvCopy(distortedProjectorImage, projectorInitImages[i]);
            }
            for (int i = 0; i < projectorInitImages.length; i++) {
                if (projectorFrame != null) {
                    projectorFrame.showImage(projectorInitImages[i].getBufferedImage(1.0));
                    projectorFrame.waitLatency();
                }
                frameGrabber.trigger();
                cvCopy(frameGrabber.grab(), cameraInitImages[i]);
            }
            for (int i = 0; i < cameraInitImages.length; i++) {
                // for gamma "uncorrection", linearization
                if (cameraSettings.getResponseGamma() != 1.0) {
                    cameraInitImages[i].applyGamma(cameraSettings.getResponseGamma());
                }
                cameraDevice.undistort(cameraInitImages[i], undistortedCameraImage);
                        cvCopy(undistortedCameraImage, cameraInitImages[i]);
                cvConvertScale(undistortedCameraImage, cameraInitFloatImages[i], 1.0/255.0, 0);

                if (frameRecorder != null) {
                    undistortedCameraImage.applyGamma(1/2.2);
                    frameRecorder.record(undistortedCameraImage);
                }
            }

            // resize and tile the monitor frames according to the size of the grabbed images
            if (trackingSettings.getMonitorWindowsScale() > 0 && monitorWindows != null) {
                final double scale = trackingSettings.getMonitorWindowsScale();
                // access frame grabbers from _this_ thread *ONLY*...
                for (int i = 0; i < monitorWindows.length; i++) {
                    final CanvasFrame c = monitorWindows[i];
                    final int index = i;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            c.setCanvasSize((int)Math.round(initImage.width *scale),
                                            (int)Math.round(initImage.height*scale));
                            c.setTitle(monitorWindowsTitles[index] + " (" +
                                    initImage.width + " x " + initImage.height + "  " +
                                    (initImage.depth&~IPL_DEPTH_SIGN) + " bpp) - ProCamTracker");
                        }
                    });
                }
                CanvasFrame.tile(monitorWindows);
                CanvasFrame.global = monitorWindows[0];
                monitorWindows[0].showImage(cameraInitImages[1], trackingSettings.getMonitorWindowsScale());
            }

            // in the grabbed camera images, acquire the region of interest
            ObjectRoiAcquisition ora = trackingSettings.getObjectRoiAcquisition();
            if (ora != ObjectRoiAcquisition.USER && (trackingSettings.objectImageFile == null ||
                    (objectImage = cvLoadImage(trackingSettings.objectImageFile.getAbsolutePath())) == null)) {
                throw new Exception("Error: Could not load the object image file \"" +
                        trackingSettings.objectImageFile + "\" for " + ora + ".");
            }
            switch (ora) {
                case USER:            roiPts = acquireRoiFromUser();                              break;
                case OBJECT_FINDER:   roiPts = acquireRoiFromObjectFinder  (cameraInitImages[1]); break;
                case MARKER_DETECTOR: roiPts = acquireRoiFromMarkerDetector(cameraInitImages[1]); break;
                default: assert (false);
            }
            if (roiPts == null) {
                throw new Exception("Error: Could not acquire the ROI.");
            }

            // extract the surface reflectance image along with its geometric plane parameters
            double[] ambientLight = new double[cameraInitFloatImages[0].nChannels];
            IplImage reflectance = reflectanceInitializer.initializeReflectance(cameraInitFloatImages,
                    roiPts, ambientLight);
            String infoLogString = "initial a = (";
            for (int i = 0; i < ambientLight.length; i++) {
                infoLogString += (float)ambientLight[i];
                if (i < ambientLight.length-1) {
                    infoLogString += ", ";
                }
            }
            logger.info(infoLogString + ")");
            CvMat n = reflectanceInitializer.initializePlaneParameters(cameraInitFloatImages,
                    reflectance, roiPts, ambientLight);
            logger.info("initial n = " + n.toString(11));

            // create our image transformer and its initial parameters
            transformer = new ProCamTransformer(roiPts, cameraDevice, projectorDevice, n);
            parameters = transformer.createParameters();
            parameters.set(8,  1.0);
            for (int i = 0; i < ambientLight.length; i++) {
                parameters.set(9+i, ambientLight[i]);
            }
            lastParameters = parameters.clone();
            tempParameters = parameters.clone();

            Rectangle r = trackingSettings.chronometerBounds;
            if (r != null && r.width > 0 && r.height > 0) {
                chronometer = new Chronometer(r);
            } else {
                chronometer = null;
            }

            // compute initial projector image
            IplImage frameImage = nextFrameImage();
            if (trackingSettings.projectionType == ProjectionType.TRACKED) {
                composeWarper.setFillColor(CvScalar.WHITE);

                srcPts.put(0.0, 0.0,  frameImage.width, 0.0,
                        frameImage.width, frameImage.height,  0.0, frameImage.height);
                JavaCV.getPerspectiveTransform(srcPts.get(), roiPts, tempH);
                composeParameters.compose(parameters.getProjectorParameters(), false, parameters.getSurfaceParameters(), false);
                composeParameters.compose(composeParameters.getH(), false, tempH, false);
                composeWarper.transform(srcPts, dstPts, composeParameters, false);
                composeWarper.transform(frameImage, projectorImages[0], null, 0, composeParameters, false);
            } else {
                dstPts.put(0.0, 0.0,  projectorImages[0].width, 0.0,
                        projectorImages[0].width, projectorImages[0].height,  0.0, projectorImages[0].height);
                cvConvertScale(frameImage, projectorImages[0], 255.0, 0);
            }
            if (trackingSettings.virtualBallEnabled) {
                virtualBallSettings.setInitialRoiPts(dstPts.get());
                virtualBall = new VirtualBall(virtualBallSettings);
                virtualBall.draw(projectorImages[0], dstPts.get());
            } else {
                virtualBall = null;
            }
            cvCopy(projectorImages[0], projectorImages[1]);
            transformer.setProjectorImage(projectorImages[0], alignerSettings.getPyramidLevels());

            // show our target alignment in the first monitor frame
            if (monitorWindows != null) {
                IplImage floatImage = IplImage.createCompatible(reflectance);
                transformer.transform(reflectance, floatImage, null, 0, parameters, false);
                cvConvertScale(floatImage, undistortedCameraImage, 255, 0);
                monitorWindows[0].showImage(undistortedCameraImage, trackingSettings.getMonitorWindowsScale());

                monitorImages = new IplImage[alignerSettings.getPyramidLevels()];
            }


            setProgress(TRACKING);

            // perform tracking via iterative minimization...
            aligner = null;
            if (trackingSettings.projectionType == ProjectionType.TRACKED) {
                projectorImageIndex = 0; doProjector.run();
                if (projectorFrame != null) {
                    projectorFrame.waitLatency();
                }
            }
            frameGrabber.trigger();
            projectorImageIndex = 1; doCamera.run();
            aligner = new LMImageAligner(transformer, parameters, reflectance, roiPts,
                    undistortedCameraImage, alignerSettings);

            int timeMax = trackingSettings.getIteratingTimeMax();
            projectorImageIndex = 0;
            double totalError   = 0;
            int totalErrorCount = 0;
            double[] delta = new double[parameters.size()+1];
            long[][] iterationTime = new long[alignerSettings.getPyramidLevels()][alignerSettings.getLMLambdas().length];
            int[][] iterationCount = new int[alignerSettings.getPyramidLevels()][alignerSettings.getLMLambdas().length];
            int framesCount = 0;
            while (!isCancelled() && grabbedImage != null) {
                framesCount++;
                boolean converged = false;
                long iterationsStartTime = System.currentTimeMillis();
                int iterations = 0;
                while (!converged) {
                    int p = aligner.getPyramidLevel();
                    int l = aligner.getLmLambdaIndex();

                    long iterationStartTime = System.currentTimeMillis();
                    converged = aligner.iterate(delta);
                    long iterationEndTime = System.currentTimeMillis();

                    iterationTime[p][l] +=  iterationEndTime - iterationStartTime;
                    iterationCount[p][l]++;

                    if (timeMax > 0 && iterationEndTime-iterationsStartTime > timeMax) {
                        converged = true;
                    }
                    iterations++;
                }
                parameters = (ProCamTransformer.Parameters)aligner.getParameters();
                infoLogString = "iteratingTime = " + (System.currentTimeMillis()-iterationsStartTime) +
                        " (" + iterations + " iterations)  alignerRMSE = " + (float)aligner.getRMSE();

                // trigger camera for a new frame
                frameGrabber.trigger();

                // if we have monitor frames, display the images for feedback
                if (monitorWindows != null) {
                    int p = aligner.getPyramidLevel();
                    IplImage roiMask  = aligner.getRoiMaskImage();
                    IplImage warped   = aligner.getWarpedImage();
                    IplImage residual = aligner.getResidualImage();
                    IplImage target   = aligner.getTargetImage();
                    int channels = warped.nChannels;

                    if (monitorImages[p] == null) {
                        monitorImages[p] = IplImage.create(roiMask.width, roiMask.height, IPL_DEPTH_8U, channels);
                    }

                    FloatBuffer in  = warped.getFloatBuffer();
                    ByteBuffer mask = roiMask.getByteBuffer();
                    ByteBuffer out  = monitorImages[p].getByteBuffer();
                    while (in.hasRemaining() && out.hasRemaining() && mask.hasRemaining()) {
                        byte m = mask.get();
                        for (int z = 0; z < channels; z++) {
                            float f = in.get();
                            out.put((byte)(m == 0 ? 0 : Math.round(f*255)));
                        }
                    }
                    monitorWindows[1].showImage(monitorImages[p],trackingSettings.getMonitorWindowsScale()*(1<<p));

                    in = residual.getFloatBuffer();
                    mask.position(0);
                    out.position(0);
                    while (in.hasRemaining() && out.hasRemaining() && mask.hasRemaining()) {
                        byte m = mask.get();
                        for (int z = 0; z < channels; z++) {
                            float f = in.get();
                            out.put((byte)(m == 0 ? 128 : Math.round(f*255 + 128)));
                        }
                    }
                    monitorWindows[2].showImage(monitorImages[p],trackingSettings.getMonitorWindowsScale()*(1<<p));

                    cvSetZero(monitorImages[p]);
                    cvSetImageROI(monitorImages[p], cvGetImageROI(target));
                    cvConvertScale(target, monitorImages[p], 255, 0);
                    cvResetImageROI(monitorImages[p]);
                    // if we use the marker detector, compute the error with our tracking
                    if (trackingSettings.objectRoiAcquisition == ObjectRoiAcquisition.MARKER_DETECTOR &&
                            markerDetector != null) {
                        Marker[] markers = new Marker[4];
                        boolean missing;
                        MarkerDetector.Settings ms = new MarkerDetector.Settings();
                        ms.setBinarizationKBlackMarkers(0.99);
                        do {
                            Marker[] detected = markerDetector.detect(undistortedCameraImage, false);
                            for (Marker m : detected) {
                                if (markers[m.id] == null) {
                                    markers[m.id] = m;
                                }
                            }
                            missing = false;
                            for (Marker m : markers) {
                                if (m == null) {
                                    missing = true;
                                }
                            }
                            ms.setBinarizationKBlackMarkers(ms.getBinarizationKBlackMarkers()-0.05);
                            markerDetector.setSettings(ms);
                        } while (missing && ms.getBinarizationKBlackMarkers() > 0);

                        transformer.transform(srcPts, dstPts, parameters, false);

                        infoLogString += "  markerDistances = ";
                        for (int j = 0; j < 4; j++) {
                            for (Marker m : markers) {
                                if (m.id == j) {
                                    double[] center = m.getCenter();
                                    double dx = center[0] - dstPts.get(j*2);
                                    double dy = center[1] - dstPts.get(j*2+1);
                                    double error = dx*dx + dy*dy;
                                    infoLogString += j + ": " + (float)Math.sqrt(error) + "  ";
                                    totalError += error;
                                    totalErrorCount++;

                                    CvPoint.fillArray(corners, (byte)(16-p), m.corners);
                                    cvLine(monitorImages[p], corners[0].byValue(), corners[2].byValue(),
                                            CV_RGB(monitorImages[p].getMaxIntensity(), 0, 0), 1, CV_AA, 16);
                                    cvLine(monitorImages[p], corners[1].byValue(), corners[3].byValue(),
                                            CV_RGB(monitorImages[p].getMaxIntensity(), 0, 0), 1, CV_AA, 16);
                                    break;
                                }
                            }
                            temppts[j].x = (int)Math.round(dstPts.get(j*2)   * (1<<16-p));
                            temppts[j].y = (int)Math.round(dstPts.get(j*2+1) * (1<<16-p));
                            temppts[j].write();
                        }
                        infoLogString += " RMSE: " + (float)Math.sqrt(totalError/totalErrorCount);

                        cvPolyLine(monitorImages[p], temppts[0].pointerByReference(), new int[] { 4 }, 1, 1,
                                CV_RGB(0, monitorImages[p].getMaxIntensity(), 0), 1, CV_AA, 16);
                    } else {
                        dstPts.put(roiPts);
                        transformer.transform(dstPts, dstPts, parameters, false);
                        CvPoint.fillArray(temppts, (byte)(16-p), dstPts.get());
                        for (CvPoint cp : temppts) { cp.write(); }
                        cvPolyLine(monitorImages[p], temppts[0].pointerByReference(), new int[] { 4 }, 1, 1,
                                CV_RGB(0, monitorImages[p].getMaxIntensity(), 0), 1, CV_AA, 16);
                    }
                    monitorWindows[3].showImage(monitorImages[p],trackingSettings.getMonitorWindowsScale()*(1<<p));
                    if (frameRecorder != null) {
                        cvResize(monitorImages[p], undistortedCameraImage, CV_INTER_LINEAR);
                        undistortedCameraImage.applyGamma(1/2.2);
                        frameRecorder.record(undistortedCameraImage);
                    }
                }
                logger.info(infoLogString);

                // if it looks like we had a better estimate before, switch back
                if (trackingSettings.hysteresisPixelCount > 0) {
                    int p     = aligner.getPyramidLevel();
                    int newp  = Math.max(p, p + (int)Math.round(Math.log(aligner.getPixelCount()/
                            trackingSettings.hysteresisPixelCount)/Math.log(4)));
                    if (newp != p) {
                        aligner.setPyramidLevel(newp);
                    }
                    double RMSE = aligner.getRMSE();
                    tempParameters.set(parameters);
                    aligner.setParameters(lastParameters);
                    double lastRMSE = aligner.getRMSE();
                    if (RMSE < lastRMSE) {
                        aligner.setParameters(tempParameters);
                        lastParameters.set(tempParameters);
                    }
                }

                // update the projector and camera images 
                if (trackingSettings.projectionType == ProjectionType.FIXED) {
                    doCamera.run();
                } else {
                    Parallel.run(doCamera, doProjector);
                }
                projectorImageIndex = (projectorImageIndex+1)%2;
            }
            if (frameRecorder != null) {
                frameRecorder.stop();
            }
            int totalIterations = 0;
            infoLogString = "[pyramidLevel, lmLambdaIndex] averageTime averageIterations\n";
            for (int i = 0; i < iterationTime.length; i++) {
                for (int j = 0; j < iterationTime[i].length; j++) {
                    infoLogString += "[" + i + ", " + j + "] " + (iterationCount[i][j] == 0 ? 0 :
                        (float)iterationTime[i][j]/iterationCount[i][j] + " " +
                        (float)iterationCount[i][j]/framesCount) + "\n";
                    totalIterations += iterationCount[i][j];
                }
            }
            infoLogString += "Total average number of iterations: " + (float)totalIterations/framesCount;
            logger.info(infoLogString);
        } catch (Throwable t) {
            if (!isCancelled()) {
                while (t.getCause() != null) { t = t.getCause(); }
                logger.log(Level.SEVERE, "Could not perform tracking.", t);
                cancel(false);
            }
        }

        try {
            if (frameGrabber != null) {
                frameGrabber.stop();
                frameGrabber.release();
            }
            if (videoToProject != null) {
                videoToProject.stop();
                videoToProject.release();
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not release frame grabber resources", ex);
        }
        frameGrabber = null;
        videoToProject = null;

        return null;
    }

    // synchronized with doInBackground()...
    @Override protected synchronized void done() {
        // dispose of canvas frames on the Event Dispatcher Thread...
        if (monitorWindows != null) {
            for (int i = 0; i < monitorWindows.length; i++) {
                if (monitorWindows[i] != null) {
                    monitorWindows[i].dispose();
                    monitorWindows[i] = null;
                }
            }
        }
        if (projectorFrame != null) {
            projectorFrame.dispose();
            projectorFrame = null;
        }
    }

}
