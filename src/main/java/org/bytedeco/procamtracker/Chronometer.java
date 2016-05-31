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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.opencv_core.*;

/**
 *
 * @author Samuel Audet
 */
public class Chronometer {
    Chronometer(Rectangle roi, IplImage image) {
        this(roi, image, -1);
    }
    Chronometer(Rectangle roi, IplImage image, long startTime) {
        this.roi = (Rectangle)roi.clone();
        this.startTime = startTime;
        this.converter = new OpenCVFrameConverter.ToIplImage();
        this.chronoImage = new BufferedImage(roi.width, roi.height, Java2DFrameConverter.getBufferedImageType(converter.convert(image)));
        this.chronoGraphics = (Graphics2D)chronoImage.getGraphics();

        Font bigFont, smallFont;
        FontMetrics bigFontMetrics;
        Rectangle2D bounds;
        int fontSize = 10;
        do {
            bigFont   = new Font("Sans", Font.BOLD, fontSize);
            smallFont = new Font("Sans", Font.BOLD, fontSize*9/10);
            bigFontMetrics = chronoGraphics.getFontMetrics(bigFont);
            bounds = bigFontMetrics.getStringBounds("0′00″0", chronoGraphics);
            fontSize += 1;
        } while (bounds.getWidth()*1.1 < roi.width && bounds.getHeight()*1.1 < roi.height);
        this.bigFont = bigFont;
        this.smallFont = smallFont;
        this.bigFontMetrics = bigFontMetrics;
        this.bounds = bounds;

        chronoGraphics.setBackground(Color.WHITE);
        chronoGraphics.setColor(Color.BLACK);
        chronoGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private Rectangle roi;
    private long startTime;
    private OpenCVFrameConverter converter;
    private BufferedImage chronoImage;
    private Graphics2D chronoGraphics;
    private Font bigFont, smallFont;
    private FontMetrics bigFontMetrics;
    private Rectangle2D bounds;

    public void draw(IplImage image) {
        long time;
        if (startTime < 0) {
            startTime = System.currentTimeMillis();
            time = 0;
        } else {
            time = System.currentTimeMillis() - startTime;
        }
        long minutes = time/1000/60;
        long seconds = time/1000 - minutes*60;
        long deciseconds = time/100 - seconds*10 - minutes*600;

        chronoGraphics.clearRect(0, 0, chronoImage.getWidth(), chronoImage.getHeight());
        chronoGraphics.setFont(bigFont);
        int x = (int)((roi.width -bounds.getWidth ())/2 - bounds.getX()),
            y = (int)((roi.height-bounds.getHeight())/2 - bounds.getY());
        chronoGraphics.drawString(Long.toString(minutes),  x, y); x+=bigFontMetrics.stringWidth("0");
        chronoGraphics.drawString("′",  x, y);        x+=bigFontMetrics.stringWidth("′");
        chronoGraphics.drawString((seconds < 10 ?
                          "0" : "") + seconds, x, y); x+=bigFontMetrics.stringWidth("00");
        chronoGraphics.drawString("″",  x, y);        x+=bigFontMetrics.stringWidth("″");
        chronoGraphics.setFont(smallFont);
        chronoGraphics.drawString(Long.toString(deciseconds), x, y);
        if (roi.x < 0) {
            roi.x += image.width();
        }
        if (roi.y < 0) {
            roi.y += image.height();
        }
        Java2DFrameConverter.copy(chronoImage, converter.convert(image), 1.0, image.nChannels() == 4, roi);
    }

    public static void main(String[] args) throws Exception {
        CanvasFrame frame = new CanvasFrame("Chronometer Test");
        OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
        IplImage image = IplImage.create(640, 480, IPL_DEPTH_8U, 3);
        cvSetZero(image);
        Chronometer chronometer = new Chronometer(new Rectangle(100, 100, 100, 100), image);

        for (int i = 0; i < 1000; i++) {
            Thread.sleep(100);
            cvSetZero(image);
            chronometer.draw(image);
            frame.showImage(converter.convert(image));
        }
    }
}
