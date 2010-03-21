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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import name.audet.samuel.javacv.jna.cxcore.IplImage;

/**
 *
 * @author Samuel Audet
 */
public class ChronometerDrawer {
    ChronometerDrawer(Rectangle roi) {
        this.roi = (Rectangle)roi.clone();
        this.chronoImage = new BufferedImage(roi.width, roi.height, BufferedImage.TYPE_3BYTE_BGR);
        this.chronoGraphics = (Graphics2D)chronoImage.getGraphics();
        this.bigFont = new Font("DejaVu Sans", Font.BOLD, 72);
        this.smallFont = new Font("DejaVu Sans", Font.BOLD, 64);
        this.bigFontMetrics = chronoGraphics.getFontMetrics(bigFont);

        chronoGraphics.setBackground(Color.WHITE);
        chronoGraphics.setColor(Color.BLACK);
        chronoGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private Rectangle roi;
    private BufferedImage chronoImage;
    private Graphics2D chronoGraphics;
    private Font bigFont, smallFont;
    private FontMetrics bigFontMetrics;

    public void draw(IplImage image, long time) {
        long minutes = time/1000/60;
        long seconds = time/1000 - minutes*60;
        long deciseconds = time/100 - seconds*10 - minutes*600;

        chronoGraphics.clearRect(0, 0, chronoImage.getWidth(), chronoImage.getHeight());
        chronoGraphics.setFont(bigFont);
        int x = 10, y = 60;
        chronoGraphics.drawString(""+minutes,  x, y); x+=bigFontMetrics.stringWidth("0");
        chronoGraphics.drawString("′",  x, y); x+=bigFontMetrics.stringWidth("′");
        chronoGraphics.drawString((seconds < 10 ? "0" : "") + seconds, x, y); x+=bigFontMetrics.stringWidth("00");
        chronoGraphics.drawString("″",  x, y); x+=bigFontMetrics.stringWidth("″");
        chronoGraphics.setFont(smallFont);
        chronoGraphics.drawString(""+deciseconds, x, y);
        if (roi.x < 0) {
            roi.x += image.width;
        }
        if (roi.y < 0) {
            roi.y += image.height;
        }
        image.copyFrom(chronoImage, 1.0, roi);
    }
}
