/*
 * Copyright (C) 2011 Samuel Audet
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

package com.googlecode.javacv.procamtracker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import com.googlecode.javacv.BaseSettings;
import com.googlecode.javacv.BaseChildSettings;
import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.FrameGrabber.ColorMode;
import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.HandMouse;
import com.googlecode.javacv.JavaCV;
import com.googlecode.javacv.MarkedPlane;
import com.googlecode.javacv.Marker;
import com.googlecode.javacv.MarkerDetector;
import com.googlecode.javacv.ObjectFinder;
import com.googlecode.javacv.OpenCVFrameGrabber;
import com.googlecode.javacv.ProCamTransformer;
import com.googlecode.javacv.ProjectiveDevice;
import com.googlecode.javacv.ProjectiveTransformer;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;

/**
 *
 * @author Samuel Audet
 */
public class RealityAugmentor {
    public RealityAugmentor(Settings settings,
             ObjectFinder  .Settings objectFinderSettings,
             MarkerDetector.Settings markerDetectorSettings,
             VirtualBall   .Settings virtualBallSettings,
             ProjectiveDevice camera, ProjectiveDevice projector,
             int channels) throws Exception {
        setSettings(settings);
        this.objectFinderSettings   = objectFinderSettings;
        this.markerDetectorSettings = markerDetectorSettings;
        this.virtualBallSettings    = virtualBallSettings;
        this.camera    = camera;
        this.projector = projector;
        this.channels  = channels;
    }

    public static class Settings extends BaseSettings implements CleanBeanNode.ActionableBean {

        @Override public ObjectSettings[] toArray() {
            return (ObjectSettings[])toArray(new ObjectSettings[size()]);
        }

        public Action[] actions() {
            return new Action[] { new AbstractAction("New ObjectSettings") {
                public void actionPerformed(ActionEvent e) {
                    ObjectSettings os = new ObjectSettings();
                    os.setName("ObjectSettings " + size());
                    add(os);
                }
            }};
        }
    }

    public static class ObjectSettings extends BaseSettings implements CleanBeanNode.ActionableBean {

        String name = "ObjectSettings";
        File objectImageFile = null;
        public static enum ObjectRoiAcquisition { USER, OBJECT_FINDER, MARKER_DETECTOR }
        ObjectRoiAcquisition objectRoiAcquisition = ObjectRoiAcquisition.USER;

        @Override public String getName() {
            return name;
        }
        public void setName(String name) {
            firePropertyChange("name", this.name, this.name = name);
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

        public ObjectRoiAcquisition getObjectRoiAcquisition() {
            return objectRoiAcquisition;
        }
        public void setObjectRoiAcquisition(ObjectRoiAcquisition objectRoiAcquisition) {
            this.objectRoiAcquisition = objectRoiAcquisition;
        }

        @Override public VirtualSettings[] toArray() {
            return (VirtualSettings[])toArray(new VirtualSettings[size()]);
        }

        public Action[] actions() {
            return new Action[] {
                new AbstractAction("New VirtualSettings") {
                    public void actionPerformed(ActionEvent e) {
                        VirtualSettings vs = new VirtualSettings();
                        vs.setName("VirtualSettings " + size());
                        add(vs);
                    }
                },
                new AbstractAction("Delete") {
                    public void actionPerformed(ActionEvent e) {
                        getBeanContext().remove(ObjectSettings.this);
                    }
                }
            };
        }
    }

    public static class VirtualSettings extends BaseChildSettings implements CleanBeanNode.ActionableBean {

        String name = "VirtualSettings";
        Rectangle objectHotSpot = new Rectangle();
        int desktopScreenNumber = -1;
        int desktopScreenWidth = 640;
        int desktopScreenHeight = 452;
        File projectorImageFile = null;
        File projectorVideoFile = null;
        public static enum ProjectionType { TRACKED, FIXED }
        ProjectionType projectionType = ProjectionType.TRACKED;
        Rectangle chronometerBounds = new Rectangle(0, -50, 150, 50);
        boolean virtualBallEnabled = false;

        @Override public String getName() {
            return name; 
        }
        public void setName(String name) {
            firePropertyChange("name", this.name, this.name = name);
        }

        public Rectangle getObjectHotStop() {
            return objectHotSpot;
        }
        public void setObjectHotStop(Rectangle objectHotSpot) {
            this.objectHotSpot = objectHotSpot;
        }

        public int getDesktopScreenNumber() {
            return desktopScreenNumber;
        }
        public void setDesktopScreenNumber(int desktopScreenNumber) {
            this.desktopScreenNumber = desktopScreenNumber;
        }

        public int getDesktopScreenWidth() {
            return desktopScreenWidth;
        }
        public void setDesktopScreenWidth(int desktopScreenWidth) {
            this.desktopScreenWidth = desktopScreenWidth;
        }

        public int getDesktopScreenHeight() {
            return desktopScreenHeight;
        }
        public void setDesktopScreenHeight(int desktopScreenHeight) {
            this.desktopScreenHeight = desktopScreenHeight;
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

        public ProjectionType getProjectionType() {
            return projectionType;
        }
        public void setProjectionType(ProjectionType projectionType) {
            this.projectionType = projectionType;
        }

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

        public Action[] actions() {
            return new Action[] { new AbstractAction("Delete") {
                public void actionPerformed(ActionEvent e) {
                    getBeanContext().remove(VirtualSettings.this);
                }
            }};
        }
    }

    private Settings settings;
    public Settings getSettings() {
        return settings;
    }
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    private ObjectSettings objectSettings = null;
    public ObjectSettings getObjectSettings() {
        return objectSettings;
    }
    public void setObjectSettings(ObjectSettings objectSettings) {
        this.objectSettings = objectSettings;
    }

    private VirtualSettings virtualSettings = null;
    public VirtualSettings getVirtualSettings() {
        return virtualSettings;
    }
    public void setVirtualSettings(VirtualSettings virtualSettings) {
        this.virtualSettings = virtualSettings;
    }

    private ObjectFinder  .Settings objectFinderSettings;
    private MarkerDetector.Settings markerDetectorSettings;
    private VirtualBall   .Settings virtualBallSettings;

    private ProjectiveDevice camera, projector;
    private int channels;

    private double[] roiPts = null;
    private MarkerDetector markerDetector = null;
    private GraphicsDevice desktopScreen = null;
    private Robot robot = null;
    private Image handMouseCursor = null;
    private FrameGrabber videoToProject = null;
    private IplImage imageToProject = null, objectImage = null;
    private Chronometer chronometer = null;
    private VirtualBall virtualBall = null;

    private ProjectiveTransformer composeWarper = new ProjectiveTransformer();
    private ProjectiveTransformer.Parameters composeParameters = composeWarper.createParameters();
    private CvMat srcPts = CvMat.create(4, 1, CV_64F, 2), dstPts = CvMat.create(4, 1, CV_64F, 2);
    private CvMat tempH  = CvMat.create(3, 3);
    private CvPoint temppts = new CvPoint(4), corners = new CvPoint(4), corners2 = new CvPoint(corners);
    private CvRect roi = new CvRect(), maxroi = new CvRect();
    private double markerError   = 0;
    private int markerErrorCount = 0;

    private static final Logger logger = Logger.getLogger(RealityAugmentor.class.getName());

    public void initVirtualSettings() throws Exception {
        desktopScreen = null;
        robot = null;
        handMouseCursor = null;
        videoToProject = null;
        imageToProject = null;
        chronometer = null;
        virtualBall = null;

        if (virtualSettings.desktopScreenNumber < 0 && virtualSettings.projectorImageFile == null &&
                virtualSettings.projectorVideoFile == null) {
            // if no image file is given, use our own special fractal as image :)
            imageToProject = IplImage.create(projector.imageWidth,
                    projector.imageHeight, IPL_DEPTH_8U, channels);
            IplImage tempFloat = IplImage.create(projector.imageWidth,
                    projector.imageHeight, IPL_DEPTH_32F, channels);
            projector.getRectifyingHomography(camera, tempH);
            JavaCV.fractalTriangleWave(tempFloat, tempH);
            cvConvertScale(tempFloat, imageToProject, 255, 0);
        } else if (virtualSettings.desktopScreenNumber >= 0) {
            desktopScreen = CanvasFrame.getScreenDevice(virtualSettings.desktopScreenNumber);
            robot = new Robot(desktopScreen);
            int w = virtualSettings.desktopScreenWidth,
                h = virtualSettings.desktopScreenHeight;
            if (w <= 0 || h <= 0) {
                DisplayMode dm = desktopScreen.getDisplayMode();
                w = dm.getWidth();
                h = dm.getHeight();
            }
            imageToProject = IplImage.create(w, h, IPL_DEPTH_8U, channels);
            handMouseCursor = ImageIO.read(getClass().getResource("icons/Choose.png"));
        } else if (virtualSettings.projectorVideoFile != null) {
            if (virtualSettings.projectorImageFile != null) {
                // loads alpha channel
                imageToProject = IplImage.createFrom(ImageIO.read(virtualSettings.projectorImageFile));
//                int projectWidth    = imageToProject.width();
//                int projectHeight   = imageToProject.height();
//                int projectStep     = imageToProject.widthStep();
//                int projectChannels = imageToProject.nChannels();
//                if (projectChannels == 4) {
//                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
//                        minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
//                    ByteBuffer bb = imageToProject.getByteBuffer();
//                    for (int y = 0; y < projectHeight; y++) {
//                        for (int x = 0; x < projectWidth; x++) {
//                            if (bb.get(y*projectStep + x*projectChannels) != 0) {
//                                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
//                                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
//                            }
//                        }
//                    }
//                    cvSetImageROI(imageToProject, cvRect(minX, minY, maxX-minX, maxY-minY));
//                }
            }
            try {
                videoToProject = new FFmpegFrameGrabber(virtualSettings.projectorVideoFile);
            } catch (Throwable t) {
                videoToProject = new OpenCVFrameGrabber(virtualSettings.projectorVideoFile);
            }
            if (videoToProject != null) {
                videoToProject.setColorMode(ColorMode.BGR);
                if (imageToProject != null) {
                    videoToProject.setImageWidth (imageToProject.width());
                    videoToProject.setImageHeight(imageToProject.height());
                }
                videoToProject.start();
            }
        } else if (virtualSettings.projectorImageFile != null) {
            // does not load alpha channel
            imageToProject = cvLoadImage(virtualSettings.projectorImageFile.getAbsolutePath());
            imageToProject.applyGamma(2.2);
            if (imageToProject == null) {
                throw new Exception("Error: Could not load projectorImageFile named \"" + 
                        virtualSettings.projectorImageFile + "\".");
            }
        }
    }

    public double[] acquireRoi(CanvasFrame monitorWindow, double monitorWindowScale,
            IplImage cameraImage) throws Exception {
        roiPts = null;
        markerError      = 0;
        markerErrorCount = 0;

        for (ObjectSettings os : settings.toArray()) {
            // in the grabbed camera images, acquire the region of interest
            ObjectSettings.ObjectRoiAcquisition ora = os.getObjectRoiAcquisition();
            if (ora != ObjectSettings.ObjectRoiAcquisition.USER && (os.objectImageFile == null ||
                    (objectImage = cvLoadImage(os.objectImageFile.getAbsolutePath())) == null)) {
                throw new Exception("Error: Could not load the object image file \"" +
                        os.objectImageFile + "\" for " + ora + ".");
            }
            switch (ora) {
                case USER:            roiPts = acquireRoiFromUser(monitorWindow, monitorWindowScale); break;
                case OBJECT_FINDER:   roiPts = acquireRoiFromObjectFinder  (cameraImage); break;
                case MARKER_DETECTOR: roiPts = acquireRoiFromMarkerDetector(cameraImage); break;
                default: assert false;
            }

            if (roiPts != null) {
                objectSettings = os;
                virtualSettings = null;
                for (VirtualSettings vs : objectSettings.toArray()) {
                    Rectangle r = vs.objectHotSpot;
                    if (r == null || r.width <= 0 || r.height <= 0) {
                        setVirtualSettings(vs);
                        initVirtualSettings();
                    }
                }
                break;
            }
        }

        return roiPts;
    }

    private double[] acquireRoiFromUser(final CanvasFrame monitorWindow,
            final double monitorWindowScale) throws Exception {
        if (monitorWindow == null) {
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
        if (d.width%2 == 0) {
            cx += 1;
        }
        if (d.height%2 == 0) {
            cy += 1;
        }
        Cursor cursor = t.createCustomCursor(cursorImage, new Point(cx, cy), null);
        monitorWindow.setCursor(cursor);

        final double[] roiPts = new double[8];
        final int[] count = { 0 };
        monitorWindow.getCanvas().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (count[0] < 8) {
                    roiPts[count[0]++] = e.getX()/monitorWindowScale;
                    roiPts[count[0]++] = e.getY()/monitorWindowScale;
                    Graphics2D g = monitorWindow.createGraphics();
                    g.setColor(Color.RED);
                    g.drawLine(e.getX()-7, e.getY(), e.getX()+7, e.getY());
                    g.drawLine(e.getX(), e.getY()-7, e.getX(), e.getY()+7);
                    monitorWindow.releaseGraphics(g);
                }
                if (count[0] >= 8) {
                    synchronized (roiPts) {
                        monitorWindow.getCanvas().removeMouseListener(this);
                        monitorWindow.setCursor(null);
                        roiPts.notify();
                    }
                }
            }
        });

        synchronized (roiPts) {
            roiPts.wait();
        }

//        if (monitorWindows != null) {
//            for (int i = 0; i < monitorWindows.length; i++) {
//                if (monitorWindows[i] != null) {
//                    monitorWindows[i].dispose();
//                    monitorWindows[i] = null;
//                }
//            }
//            monitorWindows = null;
//        }

        return roiPts;
    }

    private double[] acquireRoiFromObjectFinder(IplImage cameraImage) throws Exception {
        IplImage grey1, grey2;
        if (objectImage.depth() == 1) {
            grey1 = objectImage;
        } else {
            grey1 = IplImage.create(objectImage.width(),  objectImage.height(),  IPL_DEPTH_8U, 1);
            cvCvtColor(objectImage, grey1, CV_BGR2GRAY);
        }
        if (cameraImage.depth() == 1) {
            grey2 = cameraImage;
        } else {
            grey2 = IplImage.create(cameraImage.width(),  cameraImage.height(),  IPL_DEPTH_8U, 1);
            cvCvtColor(cameraImage, grey2, CV_BGR2GRAY);
        }

        objectFinderSettings.setObjectImage(grey1);
        ObjectFinder objectFinder = new ObjectFinder(objectFinderSettings);
        double[] roiPts = objectFinder.find(grey2);
        if (grey1 != objectImage) {
            grey1.release();
        }
        if (grey2 != cameraImage) {
            grey2.release();
        }
        return roiPts;
    }

    private double[] acquireRoiFromMarkerDetector(IplImage cameraImage) throws Exception {
        markerDetector = new MarkerDetector(markerDetectorSettings);
        Marker[] markersin = markerDetector.detect(objectImage, false);
        String infoLogString = "objectImage marker centers = ";
        for (int i = 0; i < 4; i++) {
            for (Marker m : markersin) {
                if (m.id == i) {
                    double[] c = m.getCenter();
                    infoLogString += m.id + ": (" + (float)c[0] + ", " + (float)c[1] + ")  ";
                    break;
                }
            }
        }
        logger.info(infoLogString);
        if (markersin == null || markersin.length == 0) {
//            throw new Exception("Error: MarkerDetector detected no markers in \"" +
//                    settings.objectImageFile + "\".");
            return null;
        }
        MarkedPlane markedPlane = new MarkedPlane(objectImage.width(), objectImage.height(), markersin, 1);

        Marker[] markersout = markerDetector.detect(cameraImage, false);
        infoLogString = "initial marker centers = ";
        if (markersout == null || markersout.length == 0 ||
                markedPlane.getTotalWarp(markersout, tempH, true) == Double.POSITIVE_INFINITY) {
//            throw new Exception("Error: MarkerDetector failed to match markers in the grabbed image.");
            return null;
        }
        srcPts.put(0.0, 0.0,  objectImage.width(), 0.0,
                objectImage.width(), objectImage.height(),  0.0, objectImage.height());
        cvPerspectiveTransform(srcPts, dstPts, tempH);
        double[] roiPts = dstPts.get();

        for (int i = 0; i < 4; i++) {
            for (Marker m : markersout) {
                if (m.id == i) {
                    double[] c = m.getCenter();
                    infoLogString += m.id + ": (" + (float)c[0] + ", " + (float)c[1] + ")  ";
                    srcPts.put(i*2  , c[0]);
                    srcPts.put(i*2+1, c[1]);
                    break;
                }
            }
        }
        logger.info(infoLogString);
        return roiPts;
    }

    public IplImage nextFrameImage(int mouseX, int mouseY, boolean mouseClick) throws Exception {
        IplImage frameImage = imageToProject;
        if (desktopScreen != null && robot != null) {
//            DisplayMode dm = desktopScreen.getDisplayMode();
//            int w = dm.getWidth(), h = dm.getHeight();
            int w = imageToProject.width(), h = imageToProject.height();
            if (mouseX >= 0 && mouseY >= 0) {
                mouseX = mouseX*w/objectImage.width();
                mouseY = mouseY*h/objectImage.height();
//                System.out.println("C  " + mouseX + " " + mouseY);
                robot.mouseMove(mouseX, mouseY);
                if (mouseClick) {
//                    System.out.println("click!!");
                    robot.mousePress  (InputEvent.BUTTON1_MASK); robot.delay(10);
                    robot.mouseRelease(InputEvent.BUTTON1_MASK); robot.delay(10);
                }
            }

            BufferedImage screenCapture = robot.createScreenCapture(new Rectangle(w, h));
//            PointerInfo pi = MouseInfo.getPointerInfo();
            if (mouseX >= 0 && mouseY >= 0) {
                Graphics2D g = screenCapture.createGraphics();
//                Point p = desktopScreen.getDefaultConfiguration().getBounds().getLocation();
//                Point l = pi.getLocation();
//                int x = l.x - p.x;
//                int y = l.y - p.y;
                g.drawImage(handMouseCursor, mouseX, mouseY, null);
            }

            int[] data = ((DataBufferInt)screenCapture.getRaster().getDataBuffer()).getData();
            int dataStep = ((SinglePixelPackedSampleModel)screenCapture.getSampleModel()).getScanlineStride();
            ByteBuffer imageData = imageToProject.getByteBuffer();
            int imageStep = imageToProject.widthStep();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = data[y*dataStep + x];
                    int p = y*imageStep + 3*x;
                    imageData.put(p    , IplImage.gamma22[(rgb      ) & 0xFF]);
                    imageData.put(p + 1, IplImage.gamma22[(rgb >> 8 ) & 0xFF]);
                    imageData.put(p + 2, IplImage.gamma22[(rgb >> 16) & 0xFF]);
                }
            }
        } else if (videoToProject != null) {
            frameImage = videoToProject.grab();
            if (frameImage == null) {
                videoToProject.stop();
                videoToProject.start();
                frameImage = videoToProject.grab();
            }
            if (imageToProject != null) {
                // merge images with alpha blending...
                ByteBuffer srcBuf = imageToProject.getByteBuffer();
                ByteBuffer dstBuf = frameImage    .getByteBuffer();
                int srcChannels = imageToProject.nChannels(), dstChannels = frameImage.nChannels();
                int srcStep     = imageToProject.widthStep(), dstStep     = frameImage.widthStep();
                int width  = Math.min(imageToProject.width(),  frameImage.width());
                int height = Math.min(imageToProject.height(), frameImage.height());
                IplROI roi = imageToProject.roi();
                if (roi != null) {
                    srcBuf.position(srcStep*roi.yOffset() + roi.xOffset()*imageToProject.nChannels());
                    dstBuf.position(dstStep*roi.yOffset() + roi.xOffset()*frameImage    .nChannels());
                    width  = roi.width();
                    height = roi.height();
                }
                int srcLine = srcBuf.position(), dstLine = dstBuf.position();
                for (int y = 0; y < height && srcLine < srcBuf.capacity() &&
                        dstLine < dstBuf.capacity(); y++) {
                    for (int x = 0; x < width; x++) {
                        int a = 128, a2 = 128, b = 0, g = 0, r = 0;
                        switch (srcChannels) {
                            default: assert false;
                            case 4: a = srcBuf.get()&0xFF; a2 = 255-a;
                            case 3: b = IplImage.gamma22[srcBuf.get()&0xFF]&0xFF;
                            case 2: g = IplImage.gamma22[srcBuf.get()&0xFF]&0xFF;
                            case 1: r = IplImage.gamma22[srcBuf.get()&0xFF]&0xFF;
                        }
                        switch (dstChannels) {
                            default: assert false;
                            case 4: dstBuf.put((byte)(a+(a2 = dstBuf.get(dstBuf.position())&0xFF)));
                            case 3: dstBuf.put((byte)((b*a + (IplImage.gamma22[dstBuf.get(dstBuf.position())&0xFF]&0xFF)*a2)/255));
                            case 2: dstBuf.put((byte)((g*a + (IplImage.gamma22[dstBuf.get(dstBuf.position())&0xFF]&0xFF)*a2)/255));
                            case 1: dstBuf.put((byte)((r*a + (IplImage.gamma22[dstBuf.get(dstBuf.position())&0xFF]&0xFF)*a2)/255));
                        }
                    }
                    srcBuf.position(srcLine += srcStep);
                    dstBuf.position(dstLine += dstStep);
                }
            }
        }

        Rectangle r = virtualSettings.chronometerBounds;
        if (r == null || r.width <= 0 || r.height <= 0) {
            chronometer = null;
        } else if (chronometer == null) {
            chronometer = new Chronometer(r);
        }
        if (chronometer != null) {
            chronometer.draw(frameImage);
        }

        return frameImage;
    }

    public boolean needsHandMouse() {
        if (virtualSettings != null && virtualSettings.desktopScreenNumber >= 0) {
            return true;
        }
        for (VirtualSettings vs : objectSettings.toArray()) {
            Rectangle r = vs.objectHotSpot;
            if (r != null && r.width > 0 && r.height > 0) {
                return true;
            }
        }
        return false;
    }

    public CvRect update(IplImage projectorImage, CvRect prevroi, HandMouse handMouse,
            ProCamTransformer.Parameters parameters) throws Exception {
        int mouseX = -1, mouseY = -1;
        boolean mouseClick = false;
        if (handMouse != null) {
            double x = handMouse.getX(), y = handMouse.getY();
//            System.out.println("A  " + x + " " + y);
            if (x >= 0 && y >= 0) {
                int w = objectImage.width(), h = objectImage.height();
                srcPts.put(0.0, 0.0,  w, 0.0,  w, h,  0.0, h);
                JavaCV.getPerspectiveTransform(srcPts.get(), roiPts, tempH);
                composeParameters.compose(parameters.getSurfaceParameters().getH(), false, tempH, false);
                dstPts.put(x, y);
                composeWarper.transform(dstPts, dstPts, composeParameters, true);
                mouseX = (int)Math.round(dstPts.get(0));
                mouseY = (int)Math.round(dstPts.get(1));
                mouseClick = handMouse.isClick();
//                System.out.println("B  " + mouseX + " " + mouseY);
            }
        }

        if (mouseClick) {
            for (VirtualSettings vs : objectSettings.toArray()) {
                Rectangle r = vs.objectHotSpot;
                if (r != null && r.contains(mouseX, mouseY)) {
                    setVirtualSettings(vs);
                    initVirtualSettings();
                    break;
                }
            }
        }

        if (virtualSettings == null) {
            return null;
        }

        IplImage frameImage = nextFrameImage(mouseX, mouseY, mouseClick);
        if (virtualSettings.projectionType == VirtualSettings.ProjectionType.TRACKED) {
            int w = frameImage.width(), h = frameImage.height();
            srcPts.put(0.0, 0.0,  w, 0.0,  w, h,  0.0, h);
            JavaCV.getPerspectiveTransform(srcPts.get(), roiPts, tempH);
            composeParameters.compose(parameters.getProjectorParameters(), true,
                                      parameters.getSurfaceParameters(), false);
            composeParameters.compose(composeParameters.getH(), false, tempH, false);
            composeWarper.transform(srcPts, dstPts, composeParameters, false);
            composeWarper.setFillColor(CvScalar.WHITE);
            if (prevroi == null) {
                composeWarper.transform(frameImage, projectorImage, null, 0, composeParameters, false);
            } else {
                double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE,
                       minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
                for (int i = 0; i < dstPts.length(); i++) {
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
                maxX = Math.min(projectorImage.width(),  maxX+3);
                maxY = Math.min(projectorImage.height(), maxY+3);

                // there seems to be something funny with memory alignment and
                // ROIs, so let's align our ROI to a 16 byte boundary just in case..
                roi.x     (Math.max(0, (int)Math.floor(minX/16)*16));
                roi.y     (Math.max(0, (int)Math.floor(minY)));
                roi.width (Math.min(projectorImage.width(),  (int)Math.ceil (maxX/16)*16) - roi.x());
                roi.height(Math.min(projectorImage.height(), (int)Math.ceil (maxY))       - roi.y());

                maxroi.x     (Math.min(prevroi.x(), roi.x()));
                maxroi.y     (Math.min(prevroi.y(), roi.y()));
                maxroi.width (Math.max(prevroi.x()+prevroi.width(),  roi.x()+roi.width())  - maxroi.x());
                maxroi.height(Math.max(prevroi.y()+prevroi.height(), roi.y()+roi.height()) - maxroi.y());

                composeWarper.transform(frameImage, projectorImage, maxroi, 0, composeParameters, false);

                prevroi.x     (roi.x());
                prevroi.y     (roi.y());
                prevroi.width (roi.width());
                prevroi.height(roi.height());
            }
        } else { // Settings.ProjectionType.FIXED
            int w = projectorImage.width(), h = projectorImage.height();
            dstPts.put(0.0, 0.0,  w, 0.0,  w, h,  0.0, h);
            if (frameImage.width() == w && frameImage.height() == h) {
                cvCopy(frameImage, projectorImage);
            } else {
                cvResize(frameImage, projectorImage);
            }
        }

        if (!virtualSettings.virtualBallEnabled) {
            virtualBall = null;
        } else if (virtualBall == null) {
            virtualBallSettings.setInitialRoiPts(dstPts.get());
            virtualBall = new VirtualBall(virtualBallSettings);
        }
        if (virtualBall != null) {
            if (prevroi != null) {
                cvSetImageROI(projectorImage, roi);
            }
            virtualBall.draw(projectorImage, dstPts.get());
        }

        return prevroi == null ? null : maxroi;
    }

    public String drawRoi(IplImage monitorImage, int pyramidLevel, IplImage cameraImage,
            ProCamTransformer transformer, ProCamTransformer.Parameters parameters) {
        String infoLogString = "";

        // if we use the marker detector, compute the error with our tracking
        if (objectSettings.objectRoiAcquisition == ObjectSettings.ObjectRoiAcquisition.MARKER_DETECTOR &&
                markerDetector != null) {
            Marker[] markers = new Marker[4];
            boolean missing;
            MarkerDetector.Settings ms = new MarkerDetector.Settings();
            ms.setBinarizationKBlackMarkers(0.99);
            do {
                Marker[] detected = markerDetector.detect(cameraImage, false);
                for (Marker m : detected) {
                    if (m.id < markers.length && markers[m.id] == null) {
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

            infoLogString += "  (";
            for (int j = 0; j < 4; j++) {
                for (Marker m : markers) {
                    if (m != null && m.id == j) {
                        double[] center = m.getCenter();
                        double dx = center[0] - dstPts.get(j*2);
                        double dy = center[1] - dstPts.get(j*2+1);
                        double error = dx*dx + dy*dy;
                        infoLogString += (float)Math.sqrt(error) + (j < 3 ? ", " : "");
                        markerError += error;
                        markerErrorCount++;

                        corners.fill((byte)(16-pyramidLevel), m.corners);
                        cvLine(monitorImage, corners.position(0), corners2.position(2),
                                CV_RGB(monitorImage.highValue(), 0, 0), 1, CV_AA, 16);
                        cvLine(monitorImage, corners.position(1), corners2.position(3),
                                CV_RGB(monitorImage.highValue(), 0, 0), 1, CV_AA, 16);
                        break;
                    }
                }
                temppts.position(j);
                temppts.x((int)Math.round(dstPts.get(j*2)   * (1<<16-pyramidLevel)));
                temppts.y((int)Math.round(dstPts.get(j*2+1) * (1<<16-pyramidLevel)));
            }
            infoLogString += ")  " + (float)Math.sqrt(markerError/markerErrorCount);

            cvPolyLine(monitorImage, temppts.position(0), new int[] { 4 }, 1, 1,
                    CV_RGB(0, monitorImage.highValue(), 0), 1, CV_AA, 16);
        } else {
            dstPts.put(roiPts);
            transformer.transform(dstPts, dstPts, parameters, false);
            temppts.fill((byte)(16-pyramidLevel), dstPts.get());
            cvPolyLine(monitorImage, temppts.position(0), new int[] { 4 }, 1, 1,
                    CV_RGB(0, monitorImage.highValue(), 0), 1, CV_AA, 16);
        }

        return infoLogString;
    }

}
