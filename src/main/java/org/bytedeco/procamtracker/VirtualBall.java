/*
 * Copyright (C) 2009,2010,2011 Samuel Audet
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

import java.awt.Color;
import java.awt.Point;
import org.bytedeco.javacv.BaseChildSettings;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.JavaCV;

import static org.bytedeco.javacpp.opencv_core.*;

/**
 *
 * @author Samuel Audet
 *
 * The model is far from physically correct, but it produces nice enough animations...
 *
 */
public class VirtualBall {
    public VirtualBall(Settings settings) {
        setSettings(settings);
    }

    public static class Settings extends BaseChildSettings {
        public Settings() { }
        public Settings(double[] roiPts) {
            setInitialRoiPts(roiPts);
        }

        double[] initialRoiPts;
        double[] initialPosition = { 0, 0 };
        double[] initialVelocity = { 0, 0 };
        double[] gravity  = { 0, 10 };
        double elasticity = 0.9;
        double friction   = 0.0;
        double stickiness = 5.0;
        double radius     = 20.0;
        CvScalar colorBGR = cvScalar(0, 0, 255, 0); // red
        CvScalar colorRGB = cvScalar(255, 0, 0, 0); // red

//        public double[] getInitialRoiPts() {
//            return initialRoiPts;
//        }
        public void setInitialRoiPts(double[] initialRoiPts) {
            this.initialRoiPts = initialRoiPts.clone();
            initialPosition[0] = initialPosition[1] = 0;
            for (int i = 0; i < initialRoiPts.length; i+=2) {
                initialPosition[0] += initialRoiPts[i  ];
                initialPosition[1] += initialRoiPts[i+1];
            }
            initialPosition[0] /= initialRoiPts.length/2;
            initialPosition[1] /= initialRoiPts.length/2;
        }

//        public double[] getInitialPosition() {
//            return initialPosition;
//        }
        public void setInitialPosition(double[] initialPosition) {
            this.initialPosition = initialPosition;
        }

        public Point getInitialVelocity() {
            Point p = new Point();
            p.setLocation(initialVelocity[0], initialVelocity[1]);
            return p;
        }
        public void setInitialVelocity(Point initialVelocity) {
            this.initialVelocity = new double[] { initialVelocity.getX(), initialVelocity.getY() };
        }

        public Point getGravity() {
            Point p = new Point();
            p.setLocation(gravity[0], gravity[1]);
            return p;
        }
        public void setGravity(Point gravity) {
            this.gravity = new double[] { gravity.getX(), gravity.getY() };
        }

        public double getElasticity() {
            return elasticity;
        }
        public void setElasticity(double elasticity) {
            this.elasticity = elasticity;
        }

        public double getFriction() {
            return friction;
        }
        public void setFriction(double friction) {
            this.friction = friction;
        }

        public double getStickiness() {
            return stickiness;
        }
        public void setStickiness(double stickiness) {
            this.stickiness = stickiness;
        }

        public double getRadius() {
            return radius;
        }
        public void setRadius(double radius) {
            this.radius = radius;
        }

        public Color getColor() {
            return new Color((float)colorRGB.val(0)/255,
                             (float)colorRGB.val(1)/255,
                             (float)colorRGB.val(2)/255);
        }
        public void setColor(Color color) {
            float[] rgb = color.getRGBComponents(null);
            this.colorRGB.val(0, rgb[0]*255);
            this.colorRGB.val(1, rgb[1]*255);
            this.colorRGB.val(2, rgb[2]*255);
            this.colorBGR.val(0, rgb[2]*255);
            this.colorBGR.val(1, rgb[1]*255);
            this.colorBGR.val(2, rgb[0]*255);
        }
    }

    private Settings settings;
    public Settings getSettings() {
        return settings;
    }
    public void setSettings(Settings settings) {
        this.settings       = settings;
        this.roiPts         = settings.initialRoiPts  .clone();
        this.insidePosition = settings.initialPosition.clone();
        this.position       = settings.initialPosition.clone();
        this.velocity       = settings.initialVelocity.clone();
    }

    private double[] roiPts, insidePosition, position, velocity;
    private double timeLeft;
    private CvPoint center = new CvPoint();

    public static double[] intersectLines(double x1, double y1,
            double x2, double y2, double x3, double y3, double x4, double y4) {
        double d  = ((y4 - y3)*(x2 - x1) - (x4 - x3)*(y2 - y1));
        double ua = ((x4 - x3)*(y1 - y3) - (y4 - y3)*(x1 - x3)) / d;
        ua = ua < 0 ? 0 : ua > 1 ? 1 : ua;
//        if (Double.isNaN(ua) || Double.isInfinite(ua)) {
//            System.out.println("oops");
//        }
//        double ub = ((x2 - x1)*(y1 - y3) - (y2 - y1)*(x1 - x3)) / d;
//        ub = ub < 0 ? 0 : ub > 1 ? 1 : ub;
//        System.out.println(ua + " " + ub);
        return new double[] { x1 + ua*(x2- x1),  y1 + ua*(y2- y1) };
    }

    public static double closestPointOnLine(double x1, double y1,
            double x2, double y2, double x3, double y3) {
        return ((x3 - x1)*(x2 - x1) + (y3 - y1)*(y2 - y1)) /
               ((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1));
    }

    public static double distanceToLine(double x1, double y1,
            double x2, double y2, double x3, double y3) {
        double u = closestPointOnLine(x1, y1, x2, y2, x3, y3);
        double rx = x1 + u*(x2 - x1) - x3;
        double ry = y1 + u*(y2 - y1) - y3;
        return Math.sqrt(rx*rx + ry*ry);
    }

    public static double[] boostFromMovingLine(double x1, double y1, double x2, double y2,
            double x1p, double y1p, double x2p, double y2p, double x3, double y3) {
        double u  = closestPointOnLine(x1,  y1,  x2,  y2,  x3, y3);
        double up = closestPointOnLine(x1p, y1p, x2p, y2p, x3, y3);
        double cx1  = x1  + u *(x2  - x1);
        double cy1  = y1  + u *(y2  - y1);
        double cx1p = x1p + up*(x2p - x1p);
        double cy1p = y1p + up*(y2p - y1p);
        double boostx = 0, boosty = 0;
        if ((x3-cx1)*(cx1p-cx1) + (y3-cy1)*(cy1p-cy1) > 0) {
            boostx = cx1p - cx1;
            boosty = cy1p - cy1;
            //double boost = Math.sqrt(boostx*boostx + boosty*boosty);
        }
        return new double[] { boostx, boosty };
    }


    private boolean rollOffMovingLine(double x1, double y1, double x2, double y2,
            double x1p, double y1p, double x2p, double y2p) {
        double[] n = JavaCV.unitize(y1p-y2p, x2p-x1p);
        if ((insidePosition[0]-x1)*n[0] + (insidePosition[1]-y1)*n[1] < 0) {
            n[0] = -n[0]; n[1] = -n[1];
        }
        if (n[0]*settings.gravity[0] + n[1]*settings.gravity[1] >= 0) {
            // no support
            return false;
        }

        double vx  = velocity[0]*timeLeft, vy = velocity[1]*timeLeft;
        double x3  = position[0],    y3  = position[1];
        double x3p = position[0]+vx, y3p = position[1]+vy;

        double dist  = distanceToLine(x1, y1, x2, y2, x3,  y3);
        double distp = distanceToLine(x1, y1, x2, y2, x3p, y3p);
        if (dist < settings.radius+1.0 && dist > settings.radius-1.0 &&
                distp < settings.radius+1.0 && distp > settings.radius-1.0) {
            double[] l = JavaCV.unitize(x2p-x1p, y2p-y1p);

            double v = l[0]*(velocity[0] + settings.gravity[0]) +
                       l[1]*(velocity[1] + settings.gravity[1]);
            if (v > 0) {
                v = Math.max(0, v - settings.friction);
            } else {
                v = Math.min(0, v + settings.friction);
            }
            velocity[0] = l[0]*v;
            velocity[1] = l[1]*v;

            double[] boost = boostFromMovingLine(x1, y1, x2, y2,
                    x1p, y1p, x2p, y2p, x3, y3);
            double b = Math.sqrt(boost[0]*boost[0] + boost[1]*boost[1]);
            if (b > settings.stickiness) {
                velocity[0] += boost[0]*(1 + settings.radius/b);
                velocity[1] += boost[1]*(1 + settings.radius/b);
            } else if (b > 0.0) {
                position[0] += boost[0]*(1 + settings.radius/b);
                position[1] += boost[1]*(1 + settings.radius/b);
            }

            vx  = velocity[0]*timeLeft; vy  = velocity[1]*timeLeft;
            x3p = position[0]+vx;       y3p = position[1]+vy;
            if (distanceToLine(x1p, y1p, x2p, y2p, x3p, y3p) < settings.radius*0.999) {
                timeLeft = 0;
                velocity[0] = 0;
                velocity[1] = 0;
                //System.out.println("bad rolling = " + problem + " " + timeLeft);
            }

    //System.out.println(velocity[0]);
            return true;
        }

        return false;
    }

    private boolean bounceOffMovingLine(double x1, double y1, double x2, double y2,
            double x1p, double y1p, double x2p, double y2p) {
        boolean bounced = false;
        double vx  = velocity[0]*timeLeft, vy = velocity[1]*timeLeft;
        double x3  = position[0],    y3  = position[1];
        double x3p = position[0]+vx, y3p = position[1]+vy;

        if (Math.signum((x2p-x1p)*(y3p-y1p)-(y2p-y1p)*(x3p-x1p)) !=
                Math.signum((x2-x1)*(y3-y1)-(y2-y1)*(x3-x1)) ||
                distanceToLine(x1p, y1p, x2p, y2p, x3p, y3p) < settings.radius*0.999) {
            double[] l = JavaCV.unitize(x2p-x1p, y2p-y1p);
            double[] n = JavaCV.unitize(y1p-y2p, x2p-x1p);
            if ((insidePosition[0]-x1)*n[0] + (insidePosition[1]-y1)*n[1] < 0) {
                n[0] = -n[0]; n[1] = -n[1];
            }
            double[] p = intersectLines(x1p + settings.radius*(n[0] + l[0]), y1p + settings.radius*(n[1] + l[1]),
                    x2p + settings.radius*(n[0] - l[0]), y2p + settings.radius*(n[1] - l[1]), x3, y3, x3p, y3p);
            double dx = p[0]-position[0];
            double dy = p[1]-position[1];
            position[0] = p[0];
            position[1] = p[1];
            double timeTaken = Math.sqrt(dx*dx + dy*dy)/Math.sqrt(vx*vx + vy*vy);
            double vn = velocity[0]*n[0] + velocity[1]*n[1];
//System.out.println(timeTaken);
//System.out.println(vn);
            if (timeTaken > 0.0 && vn < 0) {
                if (Math.sqrt(velocity[0]*velocity[0] + velocity[1]*velocity[1]) < 1.0) {
                    timeLeft = 0;
                    velocity[0] = 0;
                    velocity[1] = 0;
                } else {
                    if (timeTaken >= 1.0) {
                        timeLeft = 0;
                    } else {
                        timeLeft -= timeTaken*timeLeft;
                    }
                    velocity[0] = (velocity[0] - 2*n[0]*vn)*settings.elasticity;
                    velocity[1] = (velocity[1] - 2*n[1]*vn)*settings.elasticity;
                    bounced = true;
                }
            }

            double[] boost = boostFromMovingLine(x1, y1, x2, y2,
                    x1p, y1p, x2p, y2p, x3, y3);
            double b = Math.sqrt(boost[0]*boost[0] + boost[1]*boost[1]);
            if (b > settings.stickiness) {
                velocity[0] += boost[0]*(1 + settings.radius/b);
                velocity[1] += boost[1]*(1 + settings.radius/b);
                bounced = true;
            } else if (b > 0.0) {
                position[0] += boost[0]*(1 + settings.radius/b);
                position[1] += boost[1]*(1 + settings.radius/b);
                bounced = true;
            }
        }
        return bounced;
    }

    public void draw(IplImage image, double[] roiPts) {
        assert (this.roiPts.length == roiPts.length);
        insidePosition[0] = position[0];
        insidePosition[1] = position[1];

        timeLeft = 1.0;
        boolean rolledOff = false;
        int l = roiPts.length;
        for (int i = 0; i < l && timeLeft > 0; i+=2) {
            double x1 = this.roiPts[ i%l   ], y1 = this.roiPts[(i+1)%l],
                   x2 = this.roiPts[(i+2)%l], y2 = this.roiPts[(i+3)%l];
            double x3 = roiPts[ i%l   ], y3 = roiPts[(i+1)%l],
                   x4 = roiPts[(i+2)%l], y4 = roiPts[(i+3)%l];
            if (rollOffMovingLine(x1, y1, x2, y2, x3, y3, x4, y4)) {
//                System.out.println("rolled off " + i/2);
                rolledOff = true;
                break;
            }
        }

        if (!rolledOff) {
            // free fall
            velocity[0] += settings.gravity[0];
            velocity[1] += settings.gravity[1];
        }

        boolean bouncing = true;
        while (bouncing) {
            bouncing = false;
            for (int i = 0; i < l && timeLeft > 0; i+=2) {
                double x1 = this.roiPts[ i%l   ], y1 = this.roiPts[(i+1)%l],
                       x2 = this.roiPts[(i+2)%l], y2 = this.roiPts[(i+3)%l];
                double x3 = roiPts[ i%l   ], y3 = roiPts[(i+1)%l],
                       x4 = roiPts[(i+2)%l], y4 = roiPts[(i+3)%l];
                if (bounceOffMovingLine(x1, y1, x2, y2, x3, y3, x4, y4)) {
    //                System.out.println("bounced off " + i/2);
                    bouncing = true;
                }
            }
        }
//System.out.println(timeLeft + " " + velocity[0] + " " + velocity[1]);

        if (timeLeft > 0) {
            position[0] += velocity[0]*timeLeft;
            position[1] += velocity[1]*timeLeft;
        }

        IplROI roi = image.roi();
        center.x((int)Math.round((position[0] - (roi != null ? roi.xOffset() : 0))*(1<<16)));
        center.y((int)Math.round((position[1] - (roi != null ? roi.yOffset() : 0))*(1<<16)));
//System.out.println("drawn at " + position[0] + " " + position[1]);
        cvCircle(image, center, (int)Math.round(settings.radius*(1<<16)),
                image.nChannels() == 4 ? settings.colorRGB : settings.colorBGR,
                CV_FILLED, CV_AA, 16);

        this.roiPts = roiPts.clone();
    }

    public static void main(String[] args) throws Exception {
        CanvasFrame frame = new CanvasFrame("Virtual Ball Test");
        IplImage image = IplImage.create(640, 960, IPL_DEPTH_8U, 3);
        cvSetZero(image);
        double[] roiPts = { 0,0, 640,0, 640,480, 0,400 };
        cvFillConvexPoly(image, new CvPoint().put((byte)16, roiPts), roiPts.length/2, CvScalar.WHITE, CV_AA, 16);
        VirtualBall virtualBall = new VirtualBall(new Settings(roiPts));

        for (int i = 0; i < 1000; i++) {
            Thread.sleep(100);
            cvSetZero(image);
            if (i == 50) {
                roiPts[5] -= 100;
            }
            if (i > 100 && i < 1200) {
                roiPts[3] += 1;
                roiPts[5] += 1;
            }
//if (i > 103) {
//    System.out.println(i);
//}
            cvFillConvexPoly(image, new CvPoint().put((byte)16, roiPts), roiPts.length/2, CvScalar.WHITE, CV_AA, 16);
            virtualBall.draw(image, roiPts);
            frame.showImage(image);
        }
    }
}
