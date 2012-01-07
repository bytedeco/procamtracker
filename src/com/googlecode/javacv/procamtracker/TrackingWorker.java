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

package com.googlecode.javacv.procamtracker;

import java.awt.EventQueue;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GLProfile;
import javax.swing.SwingWorker;
import com.googlecode.javacv.BaseChildSettings;
import com.googlecode.javacv.CameraDevice;
import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.FrameGrabber.ImageMode;
import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.GNImageAligner;
import com.googlecode.javacv.GNImageAlignerCL;
import com.googlecode.javacv.HandMouse;
import com.googlecode.javacv.JavaCVCL;
import com.googlecode.javacv.MarkerDetector;
import com.googlecode.javacv.ObjectFinder;
import com.googlecode.javacv.Parallel;
import com.googlecode.javacv.ProCamTransformer;
import com.googlecode.javacv.ProCamTransformerCL;
import com.googlecode.javacv.ProjectorDevice;
import com.googlecode.javacv.ReflectanceInitializer;
import com.jogamp.opencl.CLImage2d;
import com.jogamp.opencl.CLImageFormat.ChannelType;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.avutil.*;

/**
 *
 * @author Samuel Audet
 */
public class TrackingWorker extends SwingWorker {

    public static class Settings extends BaseChildSettings {

        int auditPyramidLevel = 3;
        int iteratingTimeMax = 120;
        double lostObjectThreshold = 0.1;
        double monitorWindowsScale = 0.5;
        File outputVideoFile = null;
        boolean useOpenCL = false;


        public int getAuditPyramidLevel() {
            return auditPyramidLevel;
        }
        public void setAuditPyramidLevel(int auditPyramidLevel) {
            this.auditPyramidLevel = auditPyramidLevel;
        }

        public int getIteratingTimeMax() {
            return iteratingTimeMax;
        }
        public void setIteratingTimeMax(int iteratingTimeMax) {
            this.iteratingTimeMax = iteratingTimeMax;
        }

        public double getLostObjectThreshold() {
            return lostObjectThreshold;
        }
        public void setLostObjectThreshold(double lostObjectThreshold) {
            this.lostObjectThreshold = lostObjectThreshold;
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
        "Residual Image", "Alternative Residual", "HandMouse Image" };
    private CanvasFrame[] monitorWindows = null;

    JavaCVCL contextCL;

    CameraDevice cameraDevice = null;
    FrameGrabber frameGrabber = null;
    ProjectorDevice projectorDevice = null;
    CanvasFrame projectorFrame = null;

    private double[] roiPts = null;
    private ProCamTransformer transformer;
    private ProCamTransformer.Parameters parameters, lastParameters, tempParameters;
    private GNImageAligner aligner;
    private ReflectanceInitializer reflectanceInitializer;
    private HandMouse handMouse = null;
    private RealityAugmentor realityAugmentor = null;

    private IplImage[] alternativeResidual, projectorInitFloatImages, projectorInitImages,
            cameraInitImages, cameraInitFloatImages, projectorImages, monitorImages;
    private IplImage grabbedImage, undistortedCameraImage, distortedProjectorImage,
            mask, transformed, residual, target;
    private CLImage2d grabbedImageCL, undistortedCameraImageCL, distortedProjectorImageCL, 
            mapxCL, mapyCL, projectorImageCL;
    private CvRect[] prevroi = { cvRect(0, 0, 0, 0), cvRect(0, 0, 0, 0) };
    private int projectorImageIndex;
    private FrameRecorder frameRecorder = null;

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

        if (trackingSettings.getMonitorWindowsScale() > 0) {
            monitorWindows = new CanvasFrame[monitorWindowsTitles.length];
            for (int i = 0; i < monitorWindows.length; i++) {
                monitorWindows[i] = new CanvasFrame(monitorWindowsTitles[i]);
            }
        } else {
            monitorWindows = null;
        }
    }

    private Runnable doCamera = new Runnable() { public void run() {
        try {
            RealityAugmentor.VirtualSettings virtualSettings = realityAugmentor.getVirtualSettings();
            if (virtualSettings != null && virtualSettings.projectionType !=
                    RealityAugmentor.VirtualSettings.ProjectionType.FIXED) {
                if (trackingSettings.useOpenCL) {
                    contextCL.writeImage(projectorImageCL, projectorImages[(projectorImageIndex+1)%2], false);
                    ((ProCamTransformerCL)transformer).setProjectorImageCL(projectorImageCL,
                            0, alignerSettings.getMaxPyramidLevel());
                } else {
                    transformer.setProjectorImage(projectorImages[(projectorImageIndex+1)%2],
                            0, alignerSettings.getMaxPyramidLevel());
                }
            }

            grabbedImage = frameGrabber.grab();
            if (grabbedImage != null) {
                // gamma "uncorrection", linearization
                double gamma = frameGrabber.getGamma();
                if (gamma != 1.0) {
                    grabbedImage.applyGamma(gamma);
                }
                if (trackingSettings.useOpenCL) {
                    contextCL.writeImage(grabbedImageCL, grabbedImage, false);
                    contextCL.remap(grabbedImageCL, undistortedCameraImageCL, mapxCL, mapyCL, frameGrabber.getSensorPattern());
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
            CvRect maxroi = realityAugmentor.update(projectorImages[projectorImageIndex],
                    prevroi[projectorImageIndex], handMouse, parameters);

            if (projectorFrame != null) {
                cvResetImageROI(distortedProjectorImage);
                cvResetImageROI(projectorImages[projectorImageIndex]);
                projectorDevice.distort(projectorImages[projectorImageIndex], distortedProjectorImage);
                if (maxroi != null) {
                    cvSetImageROI(projectorImages[projectorImageIndex], maxroi);
                    cvSetImageROI(distortedProjectorImage, maxroi);
                }
                projectorFrame.showImage(distortedProjectorImage);
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
                buffer[z] = Math.abs(in.get());
            }
            for (int z = 0; z < outChannels; z++) {
                out.put((byte)(m == 0 ? 0 : Math.round(buffer[order[z]]*255)));
            }
        }
        return monitorImages[pyramidLevel];
    }

    private boolean doTracking() throws Exception {
        setProgress(INITIALIZING);

        // grab the three camera frames for initialization
        for (int i = 0; i < projectorInitImages.length; i++) {
            if (projectorFrame != null) {
                cvResetImageROI(projectorInitImages[i]);
                projectorFrame.showImage(projectorInitImages[i]);
                projectorFrame.waitLatency();
            }
            frameGrabber.trigger();
            cvResetImageROI(cameraInitImages[i]);
            cvCopy(frameGrabber.grab(), cameraInitImages[i]);
        }

        // gamma "uncorrection", linearization
        for (int i = 0; i < cameraInitImages.length; i++) {
            double gamma = frameGrabber.getGamma();
            if (gamma != 1.0) {
                cameraInitImages[i].applyGamma(gamma);
            }
        }

        // acquire the ROI
        cameraDevice.setMapsPyramidLevel(0);
        IplImage cameraTempInit = cameraDevice.undistort(cameraInitImages[1]);
        if (monitorWindows != null) {
            monitorWindows[0].showImage(cameraTempInit, trackingSettings.getMonitorWindowsScale());
        }
        roiPts = realityAugmentor.acquireRoi(monitorWindows == null ? null : monitorWindows[0],
                trackingSettings.getMonitorWindowsScale(), cameraTempInit, 0);
        if (roiPts == null) {
            //throw new Exception("Error: Could not acquire the ROI.");
            return false;
        }

        // distortion removal and floating-point conversion for initialization
        cameraDevice.setMapsPyramidLevel(alignerSettings.getMinPyramidLevel());
        if (trackingSettings.useOpenCL) {
            mapxCL = contextCL.createCLImage(cameraDevice.getUndistortMap1());
            mapyCL = contextCL.createCLImage(cameraDevice.getUndistortMap2());
            contextCL.writeImage(mapxCL, cameraDevice.getUndistortMap1(), false);
            contextCL.writeImage(mapyCL, cameraDevice.getUndistortMap2(), false);
        }
        cvResetImageROI(undistortedCameraImage);
        for (int i = 0; i < cameraInitImages.length; i++) {
            cameraDevice.undistort(cameraInitImages[i], undistortedCameraImage);
            cvResetImageROI(cameraInitFloatImages[i]);
            cvConvertScale(undistortedCameraImage, cameraInitFloatImages[i],
                    1.0/undistortedCameraImage.highValue(), 0);

            if (frameRecorder != null) {
                undistortedCameraImage.applyGamma(1/2.2);
                frameRecorder.record(undistortedCameraImage);
            }
        }

        // extract the surface reflectance image along with its geometric plane parameters
        double[] ambientLight = new double[cameraInitFloatImages[0].nChannels() > 1 ? 3 : 1];
        IplImage reflectance = reflectanceInitializer.initializeReflectance(cameraInitFloatImages,
                roiPts, ambientLight);
        CLImage2d reflectanceCL = null;
        if (trackingSettings.useOpenCL) {
            reflectanceCL = contextCL.createCLImage(reflectance);
            contextCL.writeImage(reflectanceCL, reflectance, false);
        }
        String infoLogString = "initial a = (";
        for (int i = 0; i < ambientLight.length; i++) {
            infoLogString += (float)ambientLight[i];
            if (i < ambientLight.length-1) {
                infoLogString += ", ";
            }
        }
        logger.info(infoLogString + ")");
        logger.info("initializing plane parameters...");
        CvMat n = reflectanceInitializer.initializePlaneParameters(cameraInitFloatImages,
                reflectance, roiPts, ambientLight);
        logger.info("initial n = " + n.toString(12));
        RealityAugmentor.ObjectSettings objectSettings = realityAugmentor.getObjectSettings();
        if (objectSettings != null && objectSettings.objectRoiAcquisition ==
                RealityAugmentor.ObjectSettings.ObjectRoiAcquisition.MARKER_DETECTOR) {
            logger.info("\niteratingTime  iterations  objectiveRMSE  markerErrors  markerErrorsRunningAverage\n" +
                          "----------------------------------------------------------------------------------");
        } else {
            logger.info("\niteratingTime  iterations  objectiveRMSE\n" +
                          "----------------------------------------");
        }

        // create our image transformer and its initial parameters
        if (trackingSettings.useOpenCL) {
            transformer = new ProCamTransformerCL(contextCL, roiPts, cameraDevice, projectorDevice, n);
        } else {
            transformer = new ProCamTransformer(roiPts, cameraDevice, projectorDevice, n);
        }
        parameters = transformer.createParameters();
        parameters.set(parameters.size()-ambientLight.length-1, 1.0);
        for (int i = 0; i < ambientLight.length; i++) {
            parameters.set(parameters.size()-ambientLight.length+i, ambientLight[i]);
        }
        lastParameters = parameters.clone();
        tempParameters = parameters.clone();

        // compute initial projector image
        cvResetImageROI(projectorImages[0]);
        cvResetImageROI(projectorImages[1]);
        cvSet(projectorImages[0], CvScalar.WHITE);
        cvSet(projectorImages[1], CvScalar.WHITE);
        realityAugmentor.update(projectorImages[0], null, null, parameters);
        cvCopy(projectorImages[0], projectorImages[1]);
        if (trackingSettings.useOpenCL) {
            contextCL.writeImage(projectorImageCL, projectorImages[0], false);
            ((ProCamTransformerCL)transformer).setProjectorImageCL(projectorImageCL, 0, alignerSettings.getMaxPyramidLevel());
        } //else {
            transformer.setProjectorImage(projectorImages[0], 0, alignerSettings.getMaxPyramidLevel());
        //}

        // show our target alignment in the first monitor frame
        if (monitorWindows != null) {
            monitorImages = new IplImage[alignerSettings.getMaxPyramidLevel()+1];

            IplImage floatImage = IplImage.createCompatible(reflectance);
            transformer.transform(reflectance, floatImage, null, alignerSettings.getMinPyramidLevel(), parameters, false);
//            cvConvertScale(floatImage, undistortedCameraImage, 255, 0);
            IplImage monitorImage = getMonitorImage(floatImage, null, alignerSettings.getMinPyramidLevel());
            monitorWindows[0].showImage(monitorImage, trackingSettings.getMonitorWindowsScale());
        }


        setProgress(TRACKING);

        // perform tracking via iterative minimization...
        aligner = null;
//        RealityAugmentor.VirtualSettings virtualSettings = realityAugmentor.getVirtualSettings();
//        if (virtualSettings != null && virtualSettings.projectionType ==
//                RealityAugmentor.VirtualSettings.ProjectionType.TRACKED) {
            projectorImageIndex = 0; doProjector.run();
            if (projectorFrame != null) {
                projectorFrame.waitLatency();
            }
//        }
        frameGrabber.trigger();
        projectorImageIndex = 1; doCamera.run();
        if (trackingSettings.useOpenCL) {
            aligner = new GNImageAlignerCL((ProCamTransformerCL)transformer, parameters,
                    reflectanceCL, roiPts, undistortedCameraImageCL, alignerSettings);
        } else {
            aligner = new GNImageAligner(transformer, parameters,
                    reflectance, roiPts, undistortedCameraImage, alignerSettings);
        }
        handMouse = new HandMouse(aligner, handMouseSettings);
        alternativeResidual = new IplImage[2];

        int timeMax = trackingSettings.getIteratingTimeMax();
        projectorImageIndex = 0;
        double[] delta = new double[parameters.size()+1];
        int ps = alignerSettings.getMaxPyramidLevel()+1;
//            int ls = alignerSettings.getLineSearch().length;
//            long[][] iterationTime = new long[ps][ls];
//            int[][] iterationCount = new int[ps][ls];
        long[] iterationTime   = new long[ps];
        long[] iterationTime2  = new long[ps];
        int [] iterationCount  = new int [ps];
        int [] iterationCount2 = new int [ps];
        long totalIteratingTime2 = 0;
        int totalIterations2 = 0;
        int framesCount = 0;
        long startTime = System.currentTimeMillis();
        while ((trackingSettings.lostObjectThreshold <= 0 ||
                aligner.getRMSE() < trackingSettings.lostObjectThreshold) &&
                !isCancelled() && grabbedImage != null) {
            framesCount++;
            boolean converged = false;
            long iteratingTime = 0;
            int[] iterationsPerLevel = new int[ps];
            while (!converged) {
                int p = aligner.getPyramidLevel();
//                    int l = aligner.getLastLinePosition();

                long iterationStartTime = System.currentTimeMillis();
                converged = aligner.iterate(delta);
                long iterationEndTime = System.currentTimeMillis();

//                    iterationTime[p][l] += iterationEndTime - iterationStartTime;
//                    iterationCount[p][l]++;
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
            for (int i = 0; i < ps; i++) {
                iterations         += iterationsPerLevel[i];
                iterationCount [i] += iterationsPerLevel[i];
                iterationCount2[i] += iterationsPerLevel[i]*iterationsPerLevel[i];
            }
            infoLogString = iteratingTime + "  " + iterations + "  " + (float)aligner.getRMSE();
            totalIteratingTime2 += iteratingTime*iteratingTime;
            totalIterations2    += iterations*iterations;

            parameters = (ProCamTransformer.Parameters)aligner.getParameters();
//System.out.println(parameters);

//                IplImage res = aligner.getResidualImage();
//                double[] dstRoiPts = aligner.getTransformedRoiPts();
//                double vx = dstRoiPts[2] - dstRoiPts[0];
//                double vy = dstRoiPts[3] - dstRoiPts[1];
//                CvPoint points = new CvPoint((byte)16,
//                        dstRoiPts[0] +   vx/3,
//                        dstRoiPts[1] +   vy/3,
//                        dstRoiPts[0] + 2*vx/3,
//                        dstRoiPts[1] + 2*vy/3,
//                        (dstRoiPts[0]+dstRoiPts[2]+dstRoiPts[4]+dstRoiPts[6])/4,
//                        (dstRoiPts[1]+dstRoiPts[3]+dstRoiPts[5]+dstRoiPts[7])/4);
//                cvFillConvexPoly(res, points, 3, cvScalarAll(0.1), 8, 16);

            // trigger camera for a new frame
            frameGrabber.trigger();

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

            if (realityAugmentor.needsHandMouse()) {
                if (haveVisibleWindows) {
                    int p = handMouseSettings.getPyramidLevel();
                    if (aligner.getPyramidLevel() != p) {
                        aligner.setPyramidLevel(p);
                    }
                    alternativeResidual[0] = IplImage.createIfNotCompatible(
                            alternativeResidual[0], aligner.getMaskImage());
                    alternativeResidual[1] = IplImage.createIfNotCompatible(
                            alternativeResidual[1], aligner.getMaskImage());
                }
                handMouse.update(alternativeResidual);
            }

            if (haveVisibleWindows) {
                int p = aligner.getPyramidLevel();
                if (trackingSettings.useOpenCL) {
                    CLImage2d maskCL        = ((GNImageAlignerCL)aligner).getMaskImageCL();
                    CLImage2d transformedCL = ((GNImageAlignerCL)aligner).getTransformedImageCL();
                    CLImage2d residualCL    = ((GNImageAlignerCL)aligner).getResidualImageCL();
                    CLImage2d targetCL      = ((GNImageAlignerCL)aligner).getTargetImageCL();
                    contextCL.acquireGLObject(maskCL);
                    mask        = contextCL.readImage(maskCL,        mask,        false);
                    contextCL.releaseGLObject(maskCL);
                    transformed = contextCL.readImage(transformedCL, transformed, false);
                    residual    = contextCL.readImage(residualCL,    residual,    false);
                    target      = contextCL.readImage(targetCL,      target,      true );
                } else {
                    mask        = aligner.getMaskImage();
                    transformed = aligner.getTransformedImage();
                    residual    = aligner.getResidualImage();
                    target      = aligner.getTargetImage();
                }
                IplImage monitorImage = getMonitorImage(transformed, mask, p);
                monitorWindows[1].showImage(monitorImage, trackingSettings.getMonitorWindowsScale()*(1<<p));

                monitorImage = getMonitorImage(target, null, p);
//                cvSetZero(monitorImages[p]);
//                cvSetImageROI(monitorImages[p], cvGetImageROI(target));
//                cvConvertScale(target, monitorImages[p], 255, 0);
//                cvResetImageROI(monitorImages[p]);
                cameraDevice.setMapsPyramidLevel(0);
                IplImage cameraTempImage = cameraDevice.undistort(grabbedImage);
                infoLogString += realityAugmentor.drawRoi(monitorImage, p, cameraTempImage, transformer, parameters);
                cameraDevice.setMapsPyramidLevel(alignerSettings.getMinPyramidLevel());
                monitorWindows[2].showImage(monitorImage, trackingSettings.getMonitorWindowsScale()*(1<<p));
                if (frameRecorder != null) {
                    cvResize(monitorImage, undistortedCameraImage, CV_INTER_LINEAR);
                    undistortedCameraImage.applyGamma(1/2.2);
                    frameRecorder.record(undistortedCameraImage);
                }

                monitorImage = getMonitorImage(residual, mask, p);
                monitorWindows[3].showImage(monitorImage, trackingSettings.getMonitorWindowsScale()*(1<<p));

                p = handMouseSettings.getPyramidLevel();
                IplImage mouseImage = handMouse.getImage();
                if (alternativeResidual[0] != null) {
                    monitorWindows[4].showImage(alternativeResidual[0],
                            trackingSettings.getMonitorWindowsScale()*(1<<p));
                }
                if (mouseImage != null) {
                    monitorWindows[5].showImage(mouseImage,
                            trackingSettings.getMonitorWindowsScale()*(1<<p));
                }
            }
            logger.info(infoLogString);

            // reset to previous values outlying gain and ambient light values
            boolean resetGainAmbientLight = false;
            int gainAmbientLightStart = parameters.size()-ambientLight.length-1;
            int gainAmbientLightEnd   = parameters.size();
            for (int i = gainAmbientLightStart; i < gainAmbientLightEnd; i++) {
                double p = parameters.get(i);
                if (p < 0 || p > 2) {
                    resetGainAmbientLight = true;
                    break;
                }
            }
            if (resetGainAmbientLight) {
                for (int i = gainAmbientLightStart; i < gainAmbientLightEnd; i++) {
                    parameters.set(i, lastParameters.get(i));
                }
                aligner.setParameters(parameters);
            }
//System.out.println(parameters);

            // if it looks like we had a better estimate before, switch back
            if (trackingSettings.auditPyramidLevel >= 0) {
                if (aligner.getPyramidLevel() != trackingSettings.auditPyramidLevel) {
                    aligner.setPyramidLevel(trackingSettings.auditPyramidLevel);
                }
                double RMSE = aligner.getRMSE();
                tempParameters.set(parameters);
                aligner.setParameters(lastParameters);
                double lastRMSE = aligner.getRMSE();
                if (RMSE < lastRMSE) {
                    aligner.setParameters(tempParameters);
                }
            }

            lastParameters.set(aligner.getParameters());

            // update the projector and camera images
            RealityAugmentor.VirtualSettings virtualSettings = realityAugmentor.getVirtualSettings();
            if (virtualSettings != null && virtualSettings.projectionType ==
                    RealityAugmentor.VirtualSettings.ProjectionType.FIXED) {
                doCamera.run();
            } else {
                Parallel.run(doCamera, doProjector);
            }
            projectorImageIndex = (projectorImageIndex+1)%2;
        }
        long endTime = System.currentTimeMillis();

        long totalIteratingTime = 0;
        int totalIterations = 0;
//            infoLogString = "\nStatistics\n" +
//                              "==========\n" +
//                              "[pyramidLevel, lineSearchIndex] averageTime averageIterations\n";
//            for (int i = 0; i < iterationTime.length; i++) {
//                for (int j = 0; j < iterationTime[i].length; j++) {
//                    infoLogString += "[" + i + ", " + j + "] " + (iterationCount[i][j] == 0 ? 0 :
//                        (float)iterationTime[i][j]/iterationCount[i][j] + " " +
//                        (float)iterationCount[i][j]/framesCount) + "\n";
//                    totalIterations += iterationCount[i][j];
//                }
//            }
//            infoLogString += "totalAverageIterations = " + (float)totalIterations/framesCount;
        infoLogString = "\nStatistics\n" +
                          "==========\n" +
                          "pyramidLevel  averageTime  averageIterations\n" +
                          "--------------------------------------------\n";
        for (int i = 0; i < iterationTime.length; i++) {
            double meanTime   = (double)iterationTime[i]/iterationCount[i];
            double sqmeanTime = (double)iterationTime2[i]/iterationCount[i];
            double meanIter   = (double)iterationCount[i]/framesCount;
            double sqmeanIter = (double)iterationCount2[i]/framesCount;

            infoLogString += i + "  " + (iterationCount[i] == 0 ? "0±0" :
                (float)meanTime + "±" + (float)Math.sqrt(sqmeanTime - meanTime*meanTime)) + "  " +
                (float)meanIter + "±" + (float)Math.sqrt(sqmeanIter - meanIter*meanIter)  + "\n";
            totalIteratingTime  += iterationTime [i];
            totalIterations     += iterationCount[i];
        }
        double meanTime   = (double)totalIteratingTime /framesCount;
        double sqmeanTime = (double)totalIteratingTime2/framesCount;
        double meanIter   = (double)totalIterations    /framesCount;
        double sqmeanIter = (double)totalIterations2   /framesCount;

        infoLogString += "total  " +
                (float)meanTime + "±" + (float)Math.sqrt(sqmeanTime - meanTime*meanTime) + "  " +
                (float)meanIter + "±" + (float)Math.sqrt(sqmeanIter - meanIter*meanIter);
        logger.info(infoLogString);

        logger.info("\nTotal average time: " + (float)(endTime-startTime)/framesCount + " ms");

        return isCancelled() || grabbedImage == null;
    }

    // synchronized with done()...
    @Override protected synchronized Object doInBackground() throws Exception {
        try {
            setProgress(INITIALIZING);

            if (trackingSettings.useOpenCL) {
                contextCL = new JavaCVCL(GLProfile.getDefault());
            }

            // perform initialization of camera device
            frameGrabber = cameraDevice.createFrameGrabber();
            frameGrabber.setTriggerMode(true);
            frameGrabber.setImageMode(ImageMode.COLOR);
            if (trackingSettings.useOpenCL) {
                frameGrabber.setPixelFormat(PIX_FMT_RGBA);
            }
            frameGrabber.start();
            frameGrabber.trigger();
            IplImage image = frameGrabber.grab();
            if (trackingSettings.useOpenCL && frameGrabber.getSensorPattern() != -1L) {
                frameGrabber.setImageMode(ImageMode.RAW);
                image = frameGrabber.grab();
            }
            final IplImage initImage = image;
            final int initWidth    = initImage.width();
            final int initHeight   = initImage.height();
            final int initChannels = frameGrabber.getSensorPattern() != -1L ? 4 : initImage.nChannels();
            final int initDepth    = initImage.depth();

            if (initWidth != cameraDevice.imageWidth || initHeight != cameraDevice.imageHeight) {
                cameraDevice.rescale(initWidth, initHeight);
            }

            // resize and tile the monitor frames according to the size of the grabbed images
            if (monitorWindows != null) {
                final double scale = trackingSettings.getMonitorWindowsScale();
                // access FrameGrabber objects from _this_ thread *ONLY*...
                for (int i = 0; i < monitorWindows.length; i++) {
                    final CanvasFrame c = monitorWindows[i];
                    final int index = i;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            c.setCanvasSize((int)Math.round(initWidth *scale),
                                            (int)Math.round(initHeight*scale));
                            c.setTitle(monitorWindowsTitles[index] + " (" +
                                    initWidth + " x " + initHeight + "  " +
                                    (initDepth&~IPL_DEPTH_SIGN) + " bpp  gamma = " +
                                    frameGrabber.getGamma() + ") - ProCamTracker");
                        }
                    });
                }
                CanvasFrame.tile(monitorWindows);
                CanvasFrame.global = monitorWindows[0];
            }

            // allocate memory for all images and load video
            int minPyramidLevel = alignerSettings.getMinPyramidLevel();
            undistortedCameraImage  = IplImage.create(initWidth >> minPyramidLevel,
                    initHeight >> minPyramidLevel, initDepth, initChannels);
            distortedProjectorImage = IplImage.create(projectorDevice.imageWidth,
                    projectorDevice.imageHeight, IPL_DEPTH_8U, initChannels);
            projectorImages = new IplImage[]
                { IplImage.createCompatible(distortedProjectorImage),
                  IplImage.createCompatible(distortedProjectorImage) };
            projectorImageIndex = 0;
            if (trackingSettings.useOpenCL) {
                grabbedImageCL            = contextCL.createCLImage(initImage);
                undistortedCameraImageCL  = //contextCL.createCLImage(undistortedCameraImage);
                        contextCL.getCLContext().createImage2d(
                        undistortedCameraImage.width(), undistortedCameraImage.height(),
                        grabbedImageCL.getFormat().setImageChannelDataType(ChannelType.FLOAT));
                distortedProjectorImageCL = contextCL.createCLImage(distortedProjectorImage);
                projectorImageCL          = contextCL.createCLImage(projectorImages[0]);
            }

            realityAugmentor = new RealityAugmentor(realityAugmentorSettings,
                    objectFinderSettings, markerDetectorSettings, virtualBallSettings,
                    cameraDevice, projectorDevice, initChannels);

            // get the three projector frames for initialization
            GNImageAligner.Settings s = alignerSettings.clone();
            // prepare settings for maximum accuracy
            s.setDeltaMin(0);
            s.setLineSearch(new double[] { 1.0, 1.0/2, 1.0/4, 1.0/8, 1.0/16, 1.0/32, 1.0/64, 1.0/128 });
            s.setOutlierThresholds(new double[] { 0.0 });
            s.setZeroThresholds(new double[] { 0.0 });
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

            if (trackingSettings.outputVideoFile != null) {
                frameRecorder = new FFmpegFrameRecorder(trackingSettings.outputVideoFile,
                        undistortedCameraImage.width(), undistortedCameraImage.height());
                frameRecorder.start();
            } else {
                frameRecorder = null;
            }

            boolean done = false;
            while (!done) {
                done = doTracking();

                // forces release of native memory
                System.gc();
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
        }

        contextCL = null;

        roiPts = null;
        transformer = null;
        parameters = lastParameters = tempParameters = null;
        aligner = null;
        reflectanceInitializer = null;
        handMouse = null;
        realityAugmentor = null;

        grabbedImage = undistortedCameraImage = distortedProjectorImage = null;
        projectorInitFloatImages = projectorInitImages = null;
        cameraInitImages = cameraInitFloatImages = null;
        projectorImages = monitorImages = null;
        frameRecorder = null;

        // forces release of native memory
        System.gc();

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

        // forces release of native memory
        System.gc();
    }
}
