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

import java.awt.Component;
import java.awt.Dimension;
import org.netbeans.beaninfo.editors.StringArrayEditor;

/**
 *
 * @author Samuel Audet
 */
public class DoubleArrayEditor extends StringArrayEditor {
    @Override public Object getValue () {
        return stringsToDoubles((String[])super.getValue());
    }

    @Override public void setValue(Object value) {
        if (value instanceof String[]) {
            super.setValue(value);
        } else {
            setValue(doublesToStrings((double[])value));
        }
    }

    public static String[] doublesToStrings(double[] doubles) {
        if (doubles == null) {
            return null;
        }
        String[] strings = new String[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            strings[i] = Double.toString(doubles[i]);
        }
        return strings;
    }

    public static double[] stringsToDoubles(String[] strings) {
        if (strings == null) {
            return null;
        }
        double[] doubles = new double[strings.length];
        for (int i = 0; i < strings.length; i++) {
            try {
                doubles[i] = Double.parseDouble(strings[i]);
            } catch (NumberFormatException e) {
                doubles[i] = Double.NaN;
            }
        }
        return doubles;
    }

    @Override public String[] getStringArray () {
        return (String[])super.getValue();
    }

    @Override public void setStringArray(String[] value) {
        super.setValue(value);
    }

    @Override public Component getCustomEditor () {
        Component c = super.getCustomEditor();
        c.setPreferredSize(new Dimension(200, 200));
        return c;
    }
}
