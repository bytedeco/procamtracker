/*
 * Copyright (C) 2009,2010,2011,2012 Samuel Audet
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

package org.bytedeco.procamtracker;

import com.jogamp.opencl.CLImage2d;
import com.jogamp.opencl.CLImageFormat;
import com.jogamp.opencl.CLImageFormat.ChannelType;
import com.jogamp.opencl.gl.CLGLImage2d;
import com.jogamp.opengl.GLContext;
import java.awt.EventQueue;
import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.BaseChildSettings;
import org.bytedeco.javacv.BufferRing;
import org.bytedeco.javacv.CameraDevice;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.ImageMode;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.GLCanvasFrame;
import org.bytedeco.javacv.GNImageAligner;
import org.bytedeco.javacv.GNImageAlignerCL;
import org.bytedeco.javacv.HandMouse;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.JavaCV;
import org.bytedeco.javacv.JavaCVCL;
import org.bytedeco.javacv.MarkerDetector;
import org.bytedeco.javacv.ObjectFinder;
import org.bytedeco.javacv.ProCamTransformer;
import org.bytedeco.javacv.ProCamTransformerCL;
import org.bytedeco.javacv.ProjectorDevice;
import org.bytedeco.javacv.ReflectanceInitializer;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

/**
 *
 * @author Samuel Audet
 */
public class TrackingWorker extends SwingWorker {

    public static class Settings extends BaseChildSettings {

        int pyramidLevelAudit = 2;
        int pyramidLevelHandMouse = 2;
        int iteratingTimeMax = 50;
        double outlierRatioMax = 0.25;
        double monitorWindowsScale = 0.25;
        File outputVideoFile = null;
        boolean useOpenCL = false;
        int projectorBufferingSize = 4;
        int proCamPhaseShift = 17;

        public int getPyramidLevelAudit() {
            return pyramidLevelAudit;
        }
        public void setPyramidLevelAudit(int pyramidLevelAudit) {
            this.pyramidLevelAudit = pyramidLevelAudit;
        }

        public int getPyramidLevelHandMouse() {
            return pyramidLevelHandMouse;
        }
        public void setPyramidLevelHandMouse(int pyramidLevelHandMouse) {
            this.pyramidLevelHandMouse = pyramidLevelHandMouse;
        }

        public int getIteratingTimeMax() {
            return iteratingTimeMax;
        }
        public void setIteratingTimeMax(int iteratingTimeMax) {
            this.iteratingTimeMax = iteratingTimeMax;
        }

        public double getOutlierRatioMax() {
            return outlierRatioMax;
        }
        public void setOutlierRatioMax(double outlierRatioMax) {
            this.outlierRatioMax = outlierRatioMax;
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

        public boolean isUseOpenCL() {
            return useOpenCL;
        }
        public void setUseOpenCL(boolean useOpenCL) {
            this.useOpenCL = useOpenCL;
        }

        public int getProjectorBufferingSize() {
            return projectorBufferingSize;
        }
        public void setProjectorBufferingSize(int projectorBufferingSize) {
            this.projectorBufferingSize = projectorBufferingSize;
        }

        public int getProCamPhaseShift() {
            return proCamPhaseShift;
        }
        public void setProCamPhaseShift(int proCamPhaseShift) {
            this.proCamPhaseShift = proCamPhaseShift;
        }
    }

    CameraDevice    .Settings cameraSettings;
    ProjectorDevice .Settings projectorSettings;
    ObjectFinder    .Settings objectFinderSettings;
    MarkerDetector  .Settings markerDetectorSettings;
    GNImageAligner  .Settings alignerSettings;
    HandMouse       .Settings handMouseSettings;
    VirtualBall     .Settings virtualBallSettings;
    RealityAugmentor.Settings realityAugmentorSettings;
                     Settings trackingSettings;

    private String[] monitorWindowsTitles = {
        "Initial Alignment", "Transformed Object", "Camera Target",
        "Residual Image", "Relative Residual", "HandMouse Image" };
    private CanvasFrame[] monitorWindows = null;
    OpenCVFrameConverter.ToIplImage[] monitorConverters = null;

    JavaCVCL contextCL = null;

    CameraDevice cameraDevice = null;
    FrameGrabber frameGrabber = null;
    ProjectorDevice projectorDevice = null;
    CanvasFrame projectorFrame = null;
    OpenCVFrameConverter.ToIplImage grabberConverter = null;
    OpenCVFrameConverter.ToIplImage projectorConverter = null;

    private double[] roiPts = null;
    private ProCamTransformer transformer;
    private ProCamTransformer.Parameters parameters, lastParameters, tempParameters;
    private GNImageAligner aligner;
    private ReflectanceInitializer reflectanceInitializer;
    private HandMouse handMouse = null;
    private RealityAugmentor realityAugmentor = null;

    private IplImage[] projectorInitFloatImages, projectorInitImages,
            cameraInitImages, cameraInitFloatImages, monitorImages;
    private IplImage grabbedImage, undistortedCameraImage, distortedProjectorImage, reflectanceImage;
    private CLImage2d grabbedImageCL, undistortedCameraImageCL, reflectanceImageCL,
            cameraMapxCL, cameraMapyCL, projectorMapxCL, projectorMapyCL;
    private CLGLImage2d distortedProjectorImageCL;
    private CvRect roi = new CvRect();
    private FrameRecorder frameRecorder = null;
    private OpenCVFrameConverter.ToIplImage recorderConverter = null;

    class ProjectorBuffer implements BufferRing.ReleasableBuffer {
        public ProjectorBuffer(IplImage template, boolean allocateCL) {
            if (allocateCL) {
                image = IplImage.createCompatible(template);
                //image = contextCL.createPinnedIplImage(template.width(), template.height(), template.depth(), template.nChannels());
                imageCL = contextCL.createCLImageFrom(image);
            } else {
                image = IplImage.createCompatible(template);
                imageCL = null;
            }
            roi = cvRect(0, 0, 0, 0);
        }

        public IplImage image;
        public CLImage2d imageCL;
        public CvRect roi;

        public void release() {
            if (image != null) { image.release(); }
            if (imageCL != null) { imageCL.release(); }
            if (roi != null) { roi.deallocate(); }
        }
    }
    private BufferRing<ProjectorBuffer> projectorBufferRing;

    private static final Logger logger = Logger.getLogger(TrackingWorker.class.getName());

    public static final int INITIALIZING = 1, TRACKING = 2;

    public boolean cancel() {
        return cancel(getProgress() == INITIALIZING);
    }

    public void init() throws Exception {
        // create arrays and canvas frames on the Event Dispatcher Thread...
        if (cameraDevice == null) {
            cameraDevice = new CameraDevice(cameraSettings);
        } else {
            cameraDevice.setSettings(cameraSettings);
        }

        projectorSettings.setUseOpenGL(trackingSettings.useOpenCL);
        if (projectorDevice == null) {
            projectorDevice = new ProjectorDevice(projectorSettings);
            if (trackingSettings.useOpenCL) {
                // OpenCL uses RGBA, not BGR, so we need to invert the colorMixingMatrix
                CvMat X = projectorDevice.colorMixingMatrix;
                double[] x = X.get();
                X.put(x[8], x[7], x[6],
                      x[5], x[4], x[3],
                      x[2], x[1], x[0]);
            }
        } else {
            projectorDevice.setSettings(projectorSettings);
        }
        projectorFrame = projectorDevice.createCanvasFrame();
        projectorConverter = new OpenCVFrameConverter.ToIplImage();

        if (trackingSettings.getMonitorWindowsScale() > 0) {
            monitorWindows = new CanvasFrame[monitorWindowsTitles.length];
            monitorConverters = new OpenCVFrameConverter.ToIplImage[monitorWindowsTitles.length];
            for (int i = 0; i < monitorWindows.length; i++) {
                monitorWindows[i] = new CanvasFrame(monitorWindowsTitles[i]);
                monitorWindows[i].setCanvasScale(trackingSettings.getMonitorWindowsScale());
                monitorConverters[i] = new OpenCVFrameConverter.ToIplImage();
            }
            monitorImages = new IplImage[alignerSettings.getPyramidLevelMax() + 1];
        } else {
            monitorWindows = null;
            monitorImages = null;
        }
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private Runnable doCamera = new Runnable() { public void run() {
        final int maxLevel = alignerSettings.getPyramidLevelMax();

        try {
            RealityAugmentor.VirtualSettings virtualSettings = realityAugmentor.getVirtualSettings();
            if (aligner == null || (virtualSettings != null && virtualSettings.projectionType !=
                    RealityAugmentor.ProjectionType.FIXED)) {
                ProjectorBuffer pb = projectorBufferRing.get(1);
                if (trackingSettings.useOpenCL) {
                    ((ProCamTransformerCL)transformer).setProjectorImageCL(pb.imageCL, 0, maxLevel);
                }
                if (aligner == null || !trackingSettings.useOpenCL) {
                    // used during initialization, even for OpenCL
                    transformer.setProjectorImage(pb.image, 0, maxLevel);
                }
            }

            grabbedImage = grabberConverter.convert(frameGrabber.getDelayedFrame());
            if (grabbedImage == null) {
                grabbedImage = grabberConverter.convert(frameGrabber.grab());
            }
            if (grabbedImage != null) {
                // gamma "uncorrection", linearization
                double gamma = frameGrabber.getGamma();
                if (gamma != 1.0) {
                    Buffer buffer = grabbedImage.createBuffer();
                    int depth = OpenCVFrameConverter.getFrameDepth(grabbedImage.depth());
                    int stride = grabbedImage.widthStep() * 8 / Math.abs(depth);
                    Java2DFrameConverter.applyGamma(buffer, depth, stride, gamma);
                }
                if (trackingSettings.useOpenCL) {
                    if (aligner != null && alignerSettings.getDisplacementMax() > 0) {
                        double[] pts = aligner.getTransformedRoiPts();
                        int width  = grabbedImageCL.width;
                        int height = grabbedImageCL.height;
                        roi.x(0).y(0).width(width).height(height);
                        int padX = (int)Math.round(alignerSettings.getDisplacementMax()*width);
                        int padY = (int)Math.round(alignerSettings.getDisplacementMax()*height);
                        int align = 1<<(maxLevel+1);
                        // add +3 all around because pyrDown() needs it for smoothing
                        JavaCV.boundingRect(pts, roi, padX+3, padY+3, align, align);
                        cvSetImageROI(grabbedImage, roi);
                    } else {
                        cvResetImageROI(grabbedImage);
                    }
                    contextCL.writeImage(grabbedImageCL, grabbedImage, false);
                    cvResetImageROI(grabbedImage);
                    contextCL.remap(grabbedImageCL, undistortedCameraImageCL,
                            cameraMapxCL, cameraMapyCL, frameGrabber.getSensorPattern());
//contextCL.readImage(undistortedCameraImageCL, cameraInitFloatImages[0], true);
//monitorWindows[1].showImage(cameraInitFloatImages[0], true);
                    if (aligner != null) {
                        ((GNImageAlignerCL)aligner).setTargetImageCL(undistortedCameraImageCL);
                    }
                } else {
                    cameraDevice.undistort(grabbedImage, undistortedCameraImage);
                    if (aligner != null) {
                        aligner.setTargetImage(undistortedCameraImage);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }};

    private Runnable doProjector = new Runnable() { public void run() {
        try {
            ProjectorBuffer pb = projectorBufferRing.get(-1);
            CvRect maxroi = realityAugmentor.getUpdateRect();
            if (trackingSettings.useOpenCL) {
                if (maxroi != null) {
                    cvSetImageROI(pb.image, maxroi);
                } else {
                    cvResetImageROI(pb.image);
                }
                contextCL.writeImage(pb.imageCL, pb.image, false);
            }

            if (projectorFrame != null) {
                if (trackingSettings.useOpenCL) {
                    contextCL.acquireGLObject(distortedProjectorImageCL);
                    contextCL.remap(pb.imageCL, distortedProjectorImageCL, projectorMapxCL, projectorMapyCL);
                    contextCL.releaseGLObject(distortedProjectorImageCL);
                    //contextCL.finish();
                    ((GLCanvasFrame)projectorFrame).showImage(distortedProjectorImageCL.getGLObjectID());
                } else {
                    cvResetImageROI(distortedProjectorImage);
                    cvResetImageROI(pb.image);
                    projectorDevice.distort(pb.image, distortedProjectorImage);
                    if (maxroi != null) {
                        cvSetImageROI(pb.image, maxroi);
                        cvSetImageROI(distortedProjectorImage, maxroi);
                    }
                    projectorFrame.showImage(projectorConverter.convert(distortedProjectorImage));
                }
            }

            if (aligner != null) {
//System.out.println(frameGrabber.getDelayedTime());
                long prevDelayedTime = frameGrabber.getDelayedTime();
                frameGrabber.delayedGrab(trackingSettings.proCamPhaseShift * 1000);
                if (prevDelayedTime > (trackingSettings.proCamPhaseShift +
                        1000 / frameGrabber.getFrameRate()) * 1000) {
                    // wait for an additional vblank
                    if (trackingSettings.useOpenCL) {
                        ((GLCanvasFrame)projectorFrame).showImage(distortedProjectorImageCL.getGLObjectID());
                    } else {
                        projectorFrame.showImage(projectorConverter.convert(distortedProjectorImage));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }};

    private IplImage getMonitorImage(IplImage floatImage, IplImage maskImage, int pyramidLevel) {
        final int inChannels = floatImage.nChannels();
        final int outChannels = 3;
        final int[] order = inChannels == 3 ? new int[] { 0, 1, 2 } : new int[] { 2, 1, 0 };

        if (monitorImages[pyramidLevel] == null) {
            monitorImages[pyramidLevel] = IplImage.create(floatImage.width(), floatImage.height(), IPL_DEPTH_8U, outChannels);
        }

        FloatBuffer in  = floatImage.getFloatBuffer();
        ByteBuffer mask = maskImage == null ? null : maskImage.getByteBuffer();
        ByteBuffer out  = monitorImages[pyramidLevel].getByteBuffer();
        float[] buffer = new float[4];
        while (in.hasRemaining() && out.hasRemaining() && (mask == null || mask.hasRemaining())) {
            byte m = mask == null ? (byte)0xFF : mask.get();
            for (int z = 0; z < inChannels; z++) {
                buffer[z] = Math.max(0, Math.min(1, Math.abs(in.get())));
            }
            for (int z = 0; z < outChannels; z++) {
                out.put((byte)(m == 0 ? 0 : Math.round(buffer[order[z]]*255)));
            }
        }
        return monitorImages[pyramidLevel];
    }

    private boolean doTracking() throws Exception {
        final int minLevel = alignerSettings.getPyramidLevelMin();
        final int maxLevel = alignerSettings.getPyramidLevelMax();

        setProgress(INITIALIZING);
        frameGrabber.setImageMode(ImageMode.COLOR);

        // grab the three camera frames for initialization
        for (int i = 0; i < projectorInitImages.length; i++) {
            if (projectorFrame != null) {
                cvResetImageROI(projectorInitImages[i]);
                projectorFrame.showImage(projectorConverter.convert(projectorInitImages[i]));
                projectorFrame.waitLatency();
            }
            frameGrabber.flush();
            grabbedImage = grabberConverter.convert(frameGrabber.grab());
            cvResetImageROI(cameraInitImages[i]);
            if (grabbedImage.nChannels() == 3 && cameraInitImages[i].nChannels() == 4) {
                cvCvtColor(grabbedImage, cameraInitImages[i], CV_BGR2RGBA);
            } else if (grabbedImage.nChannels() == 4 && cameraInitImages[i].nChannels() == 3) {
                cvCvtColor(grabbedImage, cameraInitImages[i], CV_RGBA2BGR);
            } else {
                cvCopy(grabbedImage, cameraInitImages[i]);
            }
        }

        // gamma "uncorrection", linearization
        for (int i = 0; i < cameraInitImages.length; i++) {
            double gamma = frameGrabber.getGamma();
            if (gamma != 1.0) {
                Buffer buffer = cameraInitImages[i].createBuffer();
                int depth = OpenCVFrameConverter.getFrameDepth(cameraInitImages[i].depth());
                int stride = cameraInitImages[i].widthStep() * 8 / Math.abs(depth);
                Java2DFrameConverter.applyGamma(buffer, depth, stride, gamma);
            }
        }

        // acquire the ROI
        cameraDevice.setMapsPyramidLevel(0);
        IplImage cameraTempInit = cameraDevice.undistort(cameraInitImages[1]);
        if (monitorWindows != null) {
            boolean flipChannels = cameraTempInit.nChannels() == 4;
            monitorWindows[0].showImage(monitorConverters[0].convert(cameraTempInit), flipChannels);
        }
        roiPts = realityAugmentor.acquireRoi(monitorWindows == null ? null : monitorWindows[0],
                trackingSettings.getMonitorWindowsScale(), cameraTempInit, 0);
        if (roiPts == null) {
            //throw new Exception("Error: Could not acquire the ROI.");
            return false;
        }
        final RealityAugmentor.ObjectSettings objectSettings = realityAugmentor.getObjectSettings();
        final boolean surfaceHasTexture = objectSettings != null && objectSettings.isSurfaceHasTexture();
        final double[] referencePoints = surfaceHasTexture ? roiPts : null;

        // distortion removal and floating-point conversion for initialization
        cameraDevice.setMapsPyramidLevel(minLevel);
        cvResetImageROI(undistortedCameraImage);
        for (int i = 0; i < cameraInitImages.length; i++) {
            cameraDevice.undistort(cameraInitImages[i], undistortedCameraImage);
            cvResetImageROI(cameraInitFloatImages[i]);
            cvConvertScale(undistortedCameraImage, cameraInitFloatImages[i],
                    1.0/undistortedCameraImage.highValue(), 0);

//            IplImage monitorImage = getMonitorImage(cameraInitFloatImages[i], null, minLevel);
//            CanvasFrame.global.showImage(monitorImage);
//            CanvasFrame.global.waitKey();

            if (frameRecorder != null) {
                Frame frame = recorderConverter.convert(undistortedCameraImage);
                Java2DFrameConverter.applyGamma(frame, 1/2.2);
                frameRecorder.record(frame);
            }
        }

        // extract the surface reflectance image along with its geometric plane parameters
        double[] gainAmbientLight = new double[cameraInitFloatImages[0].nChannels() > 1 ? 4 : 2];
        cvResetImageROI(reflectanceImage);
        reflectanceInitializer.initializeReflectance(cameraInitFloatImages, reflectanceImage,
                roiPts, gainAmbientLight);
        if (trackingSettings.useOpenCL) {
            contextCL.writeImage(reflectanceImageCL, reflectanceImage, false);
        }
        String infoLogString = "initial a = (";
        for (int i = 1; i < gainAmbientLight.length; i++) {
            infoLogString += (float)gainAmbientLight[i];
            if (i < gainAmbientLight.length-1) {
                infoLogString += ", ";
            }
        }
        logger.info(infoLogString + ")");
        logger.info("initializing plane parameters...");
        CvMat n0 = reflectanceInitializer.initializePlaneParameters(surfaceHasTexture ?
                reflectanceImage : null, cameraInitFloatImages[2], referencePoints, roiPts, gainAmbientLight);
        logger.info("initial n = " + (n0 == null ? null : n0.toString(12)));

        // create our image transformer and its initial parameters
        transformer = trackingSettings.useOpenCL ?
                new ProCamTransformerCL(contextCL, referencePoints, cameraDevice, projectorDevice, n0) :
                new ProCamTransformer(referencePoints, cameraDevice, projectorDevice, n0);
        parameters = transformer.createParameters();
        final int gainAmbientLightStart = parameters.size() - gainAmbientLight.length;
        final int gainAmbientLightEnd   = parameters.size();
        for (int i = gainAmbientLightStart; i < gainAmbientLightEnd; i++) {
            parameters.set(i, gainAmbientLight[i-gainAmbientLightStart]);
        }
        lastParameters = parameters.clone();
        tempParameters = parameters.clone();


        setProgress(TRACKING);
        if (trackingSettings.useOpenCL && frameGrabber.getSensorPattern() != -1L) {
            frameGrabber.setImageMode(ImageMode.RAW);
        }

        if (objectSettings != null && objectSettings.roiAcquisitionMethod ==
                RealityAugmentor.RoiAcquisitionMethod.MARKER_DETECTOR) {
            logger.info("\niteratingTime  iterations  objectiveRMSE  markerErrors  markerErrorsRunningAverage\n" +
                          "----------------------------------------------------------------------------------");
        } else {
            logger.info("\niteratingTime  iterations  objectiveRMSE\n" +
                          "----------------------------------------");
        }

        // compute initial projector image and fill the projector buffer ring
        aligner = null;
        projectorBufferRing.position(0);
        ProjectorBuffer pb0 = projectorBufferRing.get();
        pb0.roi.x(0).y(0).width(pb0.image.width()).height(pb0.image.height());
        realityAugmentor.update(pb0.image, pb0.roi, -1, -1, false, parameters);
        realityAugmentor.getUpdateRect();
        cvResetImageROI(pb0.image);
        if (trackingSettings.useOpenCL) {
            contextCL.writeImage(pb0.imageCL, pb0.image, false);
        }
        for (int i = 1; i < projectorBufferRing.capacity(); i++) {
            ProjectorBuffer pb = projectorBufferRing.get(i);
            pb.roi.x(0).y(0).width(pb.image.width()).height(pb.image.height());
            cvResetImageROI(pb.image);
            cvCopy(pb0.image, pb.image);
            if (trackingSettings.useOpenCL) {
                contextCL.writeImage(pb.imageCL, pb.image, false);
            }
        }
        doProjector.run();
        if (projectorFrame != null) {
            projectorFrame.waitLatency();
        }
        frameGrabber.flush();
        doCamera.run();

        // show our target alignment in the first monitor frame
        if (monitorWindows != null) {
            transformer.transform(reflectanceImage, cameraInitFloatImages[0], null, minLevel, parameters, false);
            IplImage monitorImage = getMonitorImage(cameraInitFloatImages[0], null, minLevel);
            monitorWindows[0].showImage(monitorConverters[0].convert(monitorImage));
        }

        // perform tracking via iterative minimization
        aligner = trackingSettings.useOpenCL ? 
                new GNImageAlignerCL((ProCamTransformerCL)transformer, parameters, surfaceHasTexture ?
                        reflectanceImageCL : null, roiPts, undistortedCameraImageCL, alignerSettings) :
                new GNImageAligner(transformer, parameters, surfaceHasTexture ?
                        reflectanceImage : null, roiPts, undistortedCameraImage, alignerSettings);

        long timeMax = trackingSettings.getIteratingTimeMax()*1000000;
        double[] delta = new double[parameters.size()+1];
//        int searchLength = alignerSettings.getLineSearch().length;
//        long[][] iterationTime = new long[maxLevel+1][searchLength];
//        int[][] iterationCount = new int [maxLevel+1][searchLength];
        double[] iterationTime   = new double[maxLevel+1];
        double[] iterationTime2  = new double[maxLevel+1];
        int[] iterationCount  = new int[maxLevel+1];
        int[] iterationCount2 = new int[maxLevel+1];
        double totalIteratingTime2  = 0;
        int    totalIterationCount2 = 0;
        double totalAuditTime     = 0, totalAuditTime2     = 0;
        double totalHandMouseTime = 0, totalHandMouseTime2 = 0;
        double totalUpdateTime    = 0, totalUpdateTime2    = 0;
        double totalTime          = 0, totalTime2          = 0;
        int framesCount = 0;
        int lostCount = 0;
        while (!isCancelled() && grabbedImage != null && !Double.isNaN(aligner.getRMSE())) {
            long startTime = System.nanoTime();
            framesCount++;
            boolean converged = false;
            long iteratingTime = 0;
            int[] iterationsPerLevel = new int[maxLevel+1];
            while (!converged) {
                int p = aligner.getPyramidLevel();
//                int l = aligner.getLastLinePosition();

                long iterationStartTime = System.nanoTime();
                converged = aligner.iterate(delta);
                long iterationEndTime = System.nanoTime();

//                iterationTime[p][l] += iterationEndTime - iterationStartTime;
//                iterationCount[p][l]++;
                long time = iterationEndTime - iterationStartTime;
                iteratingTime += time;
                iterationsPerLevel[p]++;

                iterationTime [p] += time;
                iterationTime2[p] += time*time;

                if (timeMax > 0 && iteratingTime > timeMax) {
                    converged = true;
                }
            }
            int iterations = 0;
            for (int i = 0; i < iterationsPerLevel.length; i++) {
                iterations         += iterationsPerLevel[i];
                iterationCount [i] += iterationsPerLevel[i];
                iterationCount2[i] += iterationsPerLevel[i]*iterationsPerLevel[i];
            }
            infoLogString = iteratingTime/1000000 + "  " + iterations + "  " + (float)aligner.getRMSE();
            totalIteratingTime2  += iteratingTime*iteratingTime;
            totalIterationCount2 += iterations*iterations;

            parameters = (ProCamTransformer.Parameters)aligner.getParameters();
//System.out.println(parameters);

            long auditTime = System.nanoTime();
            // reset to previous values outlying gain and ambient light values
            boolean resetGainAmbientLight = false;
            int from = parameters.size() - transformer.getNumGains() - transformer.getNumBiases();
            int to   = parameters.size();
            for (int i = from; i < to; i++) {
                double p = parameters.get(i);
                if (p < 0 || p > 2) {
                    resetGainAmbientLight = true;
                    break;
                }
            }
            if (resetGainAmbientLight) {
                for (int i = from; i < to; i++) {
                    parameters.set(i, lastParameters.get(i));
                }
                aligner.setParameters(parameters);
            }
//System.out.println(parameters);

            // if it looks like we had a better estimate before, switch back
            if (trackingSettings.pyramidLevelAudit >= 0) {
                int p = trackingSettings.pyramidLevelAudit;
                if (aligner.getPyramidLevel() != p) {
                    aligner.setPyramidLevel(p);
                }
                double RMSE = aligner.getRMSE();
                tempParameters.set(parameters);
                aligner.setParameters(lastParameters);
                double lastRMSE = aligner.getRMSE();
                if (RMSE < lastRMSE) {
                    aligner.setParameters(tempParameters);
                }
            }

//System.out.println(aligner.getOutlierCount() + " " + aligner.getPixelCount() +
//        " " + (float)aligner.getOutlierCount()/aligner.getPixelCount());
            if ((trackingSettings.outlierRatioMax > 0 && aligner.getOutlierCount() >=
                     trackingSettings.outlierRatioMax * aligner.getPixelCount())) {
                if (++lostCount > 1) {
                    // lost track of object
                    break;
                }
            } else {
                lostCount = 0;
            }
            parameters = (ProCamTransformer.Parameters)aligner.getParameters();

            long handMouseTime = System.nanoTime();
            // if needed, let the HandMouse compute new coordinates
            IplImage[] images = null;
            if (realityAugmentor.needsMouse()) {
                int p = trackingSettings.pyramidLevelHandMouse;
                if (aligner.getPyramidLevel() != p) {
                    aligner.setPyramidLevel(p);
                }
                if (images == null) {
                    images = aligner.getImages();
                }
                CvRect roi = aligner.getRoi();
                double[] roiPts = aligner.getTransformedRoiPts();

//                //int w = cameraDevice.imageWidth, h = cameraDevice.imageHeight;
//                //CvRect roi = cvRect(0, 0, w, h);
//                //double[] roiPts = { 0.0, 0.0,  w, 0.0,  w, h,  0.0, h };
//                double vx = roiPts[2] - roiPts[0];
//                double vy = roiPts[3] - roiPts[1];
//                CvPoint points = new CvPoint((byte)(16 - p),
//                        roiPts[0] +   vx/3, roiPts[1] +   vy/3,
//                        roiPts[0] + 2*vx/3, roiPts[1] + 2*vy/3,
//                        (roiPts[0]+roiPts[2]+roiPts[4]+roiPts[6])/4,
//                        (roiPts[1]+roiPts[3]+roiPts[5]+roiPts[7])/4);
//                cvFillConvexPoly(images[3], points, 3, cvScalarAll(0.2), 8, 16);
//                //cvSet(images[4], CvScalar.WHITE);

                handMouse.update(images, p, roi, roiPts);
            }

            long updateTime = System.nanoTime();
            // if we have monitor frames, display the images for feedback
            boolean haveVisibleWindows = false;
            if (monitorWindows != null) {
                for (CanvasFrame w : monitorWindows) {
                    if (w.isVisible()) {
                        haveVisibleWindows = true;
                        break;
                    }
                }
            }
            if (haveVisibleWindows) {
                int p = aligner.getPyramidLevel();
                double scale = trackingSettings.getMonitorWindowsScale()*(1<<p);
                if (images == null) {
                    images = aligner.getImages();
                }
                IplImage target      = images[1];
                IplImage transformed = images[2];
                IplImage residual    = images[3];
                IplImage mask        = images[4];

                IplImage monitorImage = getMonitorImage(transformed, mask, p);
                monitorWindows[1].setCanvasScale(scale);
                monitorWindows[1].showImage(monitorConverters[1].convert(monitorImage));

                monitorImage = getMonitorImage(target, null, p);
                cameraDevice.setMapsPyramidLevel(0);
                IplImage cameraTempImage = cameraDevice.undistort(grabbedImage);
                infoLogString += realityAugmentor.drawRoi(monitorImage, p, cameraTempImage, transformer, parameters);
                cameraDevice.setMapsPyramidLevel(minLevel);
                monitorWindows[2].setCanvasScale(scale);
                monitorWindows[2].showImage(monitorConverters[2].convert(monitorImage));
                if (frameRecorder != null) {
                    cvResize(monitorImage, undistortedCameraImage, CV_INTER_LINEAR);
                    Frame frame = recorderConverter.convert(undistortedCameraImage);
                    Java2DFrameConverter.applyGamma(frame, 1/2.2);
                    frameRecorder.record(frame);
                }

                monitorImage = getMonitorImage(residual, mask, p);
                monitorWindows[3].setCanvasScale(scale);
                monitorWindows[3].showImage(monitorConverters[3].convert(monitorImage));

                IplImage relativeResidual = handMouse.getRelativeResidual();
                IplImage mouseImage = handMouse.getResultImage();
                if (relativeResidual != null) {
                    monitorWindows[4].setCanvasScale(scale);
                    monitorWindows[4].showImage(monitorConverters[4].convert(relativeResidual));
                }
                if (mouseImage != null) {
                    monitorWindows[5].setCanvasScale(scale);
                    monitorWindows[5].showImage(monitorConverters[5].convert(mouseImage));
                }
            }
            logger.info(infoLogString);

            // update the projector and camera images
            RealityAugmentor.VirtualSettings virtualSettings = realityAugmentor.getVirtualSettings();
            if (virtualSettings != null && virtualSettings.projectionType ==
                    RealityAugmentor.ProjectionType.FIXED) {
                doCamera.run();
            } else if (trackingSettings.useOpenCL) {
                doCamera.run();
                doProjector.run();
            } else {
                Future future = executor.submit(doCamera);
                doProjector.run();
                future.get();
            }

            lastParameters.set(parameters);
            ProjectorBuffer pb = projectorBufferRing.get();
            realityAugmentor.update(pb.image, pb.roi, handMouse.getX(),
                    handMouse.getY(), handMouse.isClick(), lastParameters);

            // the next camera frame will hopefully correspond to projectorBufferRing.get()
            // if not, we should play with projectorBufferingSize and proCamPhaseShift
            projectorBufferRing.position(projectorBufferRing.position()+1);

            long endTime = System.nanoTime();
            auditTime = handMouseTime - auditTime;
            handMouseTime = updateTime - handMouseTime;
            updateTime = endTime - updateTime;
            endTime = endTime - startTime;
            totalAuditTime  += auditTime;
            totalAuditTime2 += auditTime*auditTime;
            totalHandMouseTime  += handMouseTime;
            totalHandMouseTime2 += handMouseTime*handMouseTime;
            totalUpdateTime  += updateTime;
            totalUpdateTime2 += updateTime*updateTime;
            totalTime  += endTime;
            totalTime2 += endTime*endTime;
        }

        double totalIteratingTime  = 0;
        int    totalIterationCount = 0;
//        infoLogString = "\nStatistics\n" +
//                          "==========\n" +
//                          "[pyramidLevel, lineSearchIndex] averageTime averageIterations\n";
//        for (int i = 0; i < iterationTime.length; i++) {
//            for (int j = 0; j < iterationTime[i].length; j++) {
//                infoLogString += "[" + i + ", " + j + "] " + (iterationCount[i][j] == 0 ? 0 :
//                    (float)iterationTime[i][j]/iterationCount[i][j] + " " +
//                    (float)iterationCount[i][j]/framesCount) + "\n";
//                totalIterations += iterationCount[i][j];
//            }
//        }
//        infoLogString += "totalAverageIterations = " + (float)totalIterations/framesCount;
        infoLogString = "\nalignmentStatistics\n" +
                          "===================\n" +
                          "pyramidLevel  averageTime (ms)  averageIterations\n" +
                          "-------------------------------------------------\n";
        for (int i = 0; i < iterationTime.length; i++) {
            double meanTime   = (double)iterationTime[i]/iterationCount[i];
            double sqmeanTime = (double)iterationTime2[i]/iterationCount[i];
            double meanIter   = (double)iterationCount[i]/framesCount;
            double sqmeanIter = (double)iterationCount2[i]/framesCount;

            infoLogString += i + "    " + (iterationCount[i] == 0 ? "0±0" :
                (float)meanTime/1000000 + "±" + (float)Math.sqrt(sqmeanTime - meanTime*meanTime)/1000000) + "    " +
                (float)meanIter         + "±" + (float)Math.sqrt(sqmeanIter - meanIter*meanIter)  + "\n";
            totalIteratingTime  += iterationTime [i];
            totalIterationCount += iterationCount[i];
        }
        double meanTime   = (double)totalIteratingTime  /framesCount;
        double sqmeanTime = (double)totalIteratingTime2 /framesCount;
        double meanIter   = (double)totalIterationCount /framesCount;
        double sqmeanIter = (double)totalIterationCount2/framesCount;

        infoLogString += "all  " +
                (float)meanTime/1000000 + "±" + (float)Math.sqrt(sqmeanTime - meanTime*meanTime)/1000000 + "    " +
                (float)meanIter         + "±" + (float)Math.sqrt(sqmeanIter - meanIter*meanIter) + "\n";
        logger.info(infoLogString);

        totalAuditTime  /= framesCount;
        totalAuditTime2 /= framesCount;
        totalHandMouseTime  /= framesCount;
        totalHandMouseTime2 /= framesCount;
        totalUpdateTime  /= framesCount;
        totalUpdateTime2 /= framesCount;
        totalTime  /= framesCount;
        totalTime2 /= framesCount;
        logger.info("auditTime = " + (float)totalAuditTime/1000000 + "±" + (float)
                Math.sqrt(totalAuditTime2 - totalAuditTime*totalAuditTime)/1000000 + " ms");
        logger.info("handMouseTime = " + (float)totalHandMouseTime/1000000 + "±" + (float)
                Math.sqrt(totalHandMouseTime2 - totalHandMouseTime*totalHandMouseTime)/1000000 + " ms");
        logger.info("updateTime = " + (float)totalUpdateTime/1000000 + "±" + (float)
                Math.sqrt(totalUpdateTime2 - totalUpdateTime*totalUpdateTime)/1000000 + " ms");
        logger.info("totalTime = " + (float)totalTime/1000000 + "±" + (float)
                Math.sqrt(totalTime2 - totalTime*totalTime)/1000000 + " ms");

        if (aligner instanceof GNImageAlignerCL) {
            ((GNImageAlignerCL)aligner).release();
        }
        frameGrabber.getDelayedFrame();
        return isCancelled() || grabbedImage == null;
    }

    // synchronized with done()...
    @Override protected synchronized Object doInBackground() throws Exception {
        try {
            setProgress(INITIALIZING);

            // perform initialization of camera device
            // access FrameGrabber objects from _this_ thread *ONLY*...
            frameGrabber = cameraDevice.createFrameGrabber();
            frameGrabber.setImageMode(ImageMode.COLOR);
            if (trackingSettings.useOpenCL) {
                frameGrabber.setPixelFormat(AV_PIX_FMT_RGBA);
            }
            frameGrabber.start();
            grabberConverter = new OpenCVFrameConverter.ToIplImage();
            IplImage image = grabberConverter.convert(frameGrabber.grab());
            final IplImage initImage = image;
            final int initWidth    = initImage.width();
            final int initHeight   = initImage.height();
            final int initChannels = trackingSettings.useOpenCL ? 4 : initImage.nChannels();
            final int initDepth    = initImage.depth();

            if (initWidth != cameraDevice.imageWidth || initHeight != cameraDevice.imageHeight) {
                cameraDevice.rescale(initWidth, initHeight);
            }

            // resize and tile the monitor frames according to the size of the grabbed images
            if (monitorWindows != null) {
                final double initScale = trackingSettings.getMonitorWindowsScale();
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        for (int i = 0; i < monitorWindows.length; i++) {
                            monitorWindows[i].setCanvasSize(
                                    (int)Math.round(initWidth *initScale),
                                    (int)Math.round(initHeight*initScale));
                            monitorWindows[i].setTitle(monitorWindowsTitles[i] + " (" +
                                    initWidth + " x " + initHeight + "  " +
                                    (initDepth&~IPL_DEPTH_SIGN) + " bpp  gamma = " +
                                    frameGrabber.getGamma() + ") - ProCamTracker");
                        }
                    }
                });
                CanvasFrame.tile(monitorWindows);
                CanvasFrame.global = monitorWindows[0];
            }

            // allocate memory for all images and load video
            final int minLevel = alignerSettings.getPyramidLevelMin();
            undistortedCameraImage  = IplImage.create(initWidth >> minLevel,
                    initHeight >> minLevel, initDepth, initChannels);
            distortedProjectorImage = IplImage.create(projectorDevice.imageWidth,
                    projectorDevice.imageHeight, IPL_DEPTH_8U, initChannels);
            BufferRing.BufferFactory<ProjectorBuffer> projectorBufferFactory;
            projectorBufferFactory = new BufferRing.BufferFactory<ProjectorBuffer>() {
                public ProjectorBuffer create() {
                    return new ProjectorBuffer(distortedProjectorImage, false);
                }
            };
            if (trackingSettings.useOpenCL) {
                GLContext shareWith = null;
                if (projectorFrame instanceof GLCanvasFrame) {
                    shareWith = ((GLCanvasFrame)projectorFrame).getGLCanvas().getContext();
                }
                contextCL = new JavaCVCL(shareWith);

                grabbedImageCL            = //contextCL.createCLImageFrom(initImage);
                        contextCL.getCLContext().createImage2d(initWidth, initHeight,
                                new CLImageFormat(frameGrabber.getSensorPattern() != -1L ?
                                        CLImageFormat.ChannelOrder.R :
                                        CLImageFormat.ChannelOrder.RGBA, ChannelType.UNORM_INT8));
                undistortedCameraImageCL  = //contextCL.createCLImageFrom(undistortedCameraImage);
                        contextCL.getCLContext().createImage2d(
                                undistortedCameraImage.width(), undistortedCameraImage.height(),
                                new CLImageFormat(CLImageFormat.ChannelOrder.RGBA, ChannelType.FLOAT));
                cameraDevice.setMapsPyramidLevel(minLevel);
                cameraMapxCL = contextCL.writeImage(cameraMapxCL, cameraDevice.getUndistortMap1(), false);
                cameraMapyCL = contextCL.writeImage(cameraMapyCL, cameraDevice.getUndistortMap2(), false);

                if (projectorFrame != null) {
                    projectorMapxCL = contextCL.writeImage(projectorMapxCL, projectorDevice.getDistortMap1(), false);
                    projectorMapyCL = contextCL.writeImage(projectorMapyCL, projectorDevice.getDistortMap2(), false);
                    distortedProjectorImageCL = contextCL.createCLGLImageFrom(distortedProjectorImage);
                }
                projectorBufferFactory = new BufferRing.BufferFactory<ProjectorBuffer>() {
                    public ProjectorBuffer create() {
                        return new ProjectorBuffer(distortedProjectorImage, true);
                    }
                };
            }
            projectorBufferRing = new BufferRing<ProjectorBuffer>(projectorBufferFactory,
                    trackingSettings.projectorBufferingSize);

            realityAugmentor = new RealityAugmentor(realityAugmentorSettings,
                    objectFinderSettings, markerDetectorSettings, virtualBallSettings,
                    cameraDevice, projectorDevice, initChannels);

            handMouse = new HandMouse(handMouseSettings);

            // get the three projector frames for initialization
            GNImageAligner.Settings s = alignerSettings.clone();
            // prepare settings for maximum accuracy
            s.setAlphaTikhonov(0);
            s.setDeltaMin(0);
            s.setLineSearch(new double[] { 1.0, 1.0/2, 1.0/4, 1.0/8, 1.0/16, 1.0/32, 1.0/64, 1.0/128 });
            s.setThresholdsOutlier(new double[] { 0.0 });
            s.setThresholdsZero(new double[] { 0.0 });
            reflectanceInitializer = new ReflectanceInitializer(cameraDevice, projectorDevice, initChannels, s);
            projectorInitFloatImages = reflectanceInitializer.getProjectorImages();
            projectorInitImages   = new IplImage[projectorInitFloatImages.length];
            cameraInitImages      = new IplImage[projectorInitFloatImages.length];
            cameraInitFloatImages = new IplImage[projectorInitFloatImages.length];
            for (int i = 0; i < projectorInitFloatImages.length; i++) {
                projectorInitImages[i]   = IplImage.createCompatible(distortedProjectorImage);
                cameraInitImages[i]      = IplImage.create(initWidth, initHeight, initDepth, initChannels);
                cameraInitFloatImages[i] = IplImage.create(undistortedCameraImage.width(),
                        undistortedCameraImage.height(), IPL_DEPTH_32F, initChannels);
                cvConvertScale(projectorInitFloatImages[i], projectorInitImages[i], 255, 0);
                projectorDevice.distort(projectorInitImages[i], distortedProjectorImage);
                cvCopy(distortedProjectorImage, projectorInitImages[i]);
            }
            reflectanceImage = IplImage.createCompatible(cameraInitFloatImages[0]);
            if (trackingSettings.useOpenCL) {
                reflectanceImageCL = contextCL.createCLImageFrom(reflectanceImage);
            }

            if (trackingSettings.outputVideoFile != null) {
                frameRecorder = new FFmpegFrameRecorder(trackingSettings.outputVideoFile,
                        undistortedCameraImage.width(), undistortedCameraImage.height());
                frameRecorder.start();
                recorderConverter = new OpenCVFrameConverter.ToIplImage();
            } else {
                frameRecorder = null;
                recorderConverter = null;
            }

            boolean done = false;
            while (!done) {
                done = doTracking();

                // force release of native memory
                System.gc();
                Pointer.deallocateReferences();
            }
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
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not release FrameGrabber.", ex);
        } finally {
            frameGrabber = null;
            grabberConverter = null;
        }

        try {
            if (frameRecorder != null) {
                frameRecorder.stop();
                frameRecorder.release();
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not release FrameRecorder.", ex);
        } finally {
            frameRecorder = null;
            recorderConverter = null;
        }

        if (trackingSettings.useOpenCL) {
            grabbedImageCL.release();
            undistortedCameraImageCL.release();
            contextCL.releaseCLGLImage(distortedProjectorImageCL);
            reflectanceImageCL.release();
            cameraMapxCL.release();
            cameraMapyCL.release();
            projectorMapxCL.release();
            projectorMapyCL.release();
            grabbedImageCL = undistortedCameraImageCL = distortedProjectorImageCL = null;
            reflectanceImageCL = null;
            cameraMapxCL = cameraMapyCL = projectorMapxCL = projectorMapyCL = null;

            contextCL.release();
            contextCL = null;
        }

        roiPts = null;
        transformer = null;
        parameters = lastParameters = tempParameters = null;
        aligner = null;
        reflectanceInitializer = null;
        handMouse = null;
        realityAugmentor = null;

        grabbedImage = undistortedCameraImage = distortedProjectorImage = null;
        projectorInitFloatImages = projectorInitImages = null;
        cameraInitImages = cameraInitFloatImages = monitorImages = null;
        reflectanceImage = null;

        projectorBufferRing.release();
        projectorBufferRing = null;

        // force release of native memory
        System.gc();
        Pointer.deallocateReferences();

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
            projectorConverter = null;
        }

        // force release of native memory
        System.gc();
        Pointer.deallocateReferences();
    }
}
