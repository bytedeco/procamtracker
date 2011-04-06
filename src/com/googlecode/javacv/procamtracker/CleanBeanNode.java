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

package com.googlecode.javacv.procamtracker;

import java.beans.IntrospectionException;
import java.beans.PropertyEditor;
import java.beans.beancontext.BeanContext;
import java.util.HashMap;
import javax.swing.Action;
import org.openide.nodes.BeanChildren;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.PropertySupport;

/**
 *
 * @author Samuel Audet
 */
public class CleanBeanNode<T> extends BeanNode<T> {
    public CleanBeanNode(T o,
            final HashMap<String, Class<? extends PropertyEditor>> editors,
            String displayName) throws IntrospectionException {
        super(o, o instanceof BeanContext ? new BeanChildren((BeanContext)o,
                new BeanChildren.Factory() {
                    public Node createNode(Object bean) throws IntrospectionException {
                        return new CleanBeanNode<Object>(bean, editors, null);
                    }
                }) : null);

        setIconBaseWithExtension("org/openide/nodes/defaultNode.png");

        Property[] ps = getPropertySets()[0].getProperties();
        for (Property p : ps) {
            if (editors != null && editors.containsKey(p.getName())) {
                Class<? extends PropertyEditor> c = editors.get(p.getName());
                if (c == null) {
                    p.setHidden(true);
                } else {
                    ((PropertySupport.Reflection<?>)p).setPropertyEditorClass(c);
                }
            }
            if (p.getName().equals("beanContext") || p.getName().equals("beanContextChildPeer") ||
                    p.getName().equals("beanContextPeer") || p.getName().equals("class") ||
                    p.getName().equals("delegated") || p.getName().equals("designTime") ||
                    p.getName().equals("empty") || p.getName().equals("locale") ||
                    p.getName().equals("serializing")) {
                p.setHidden(true);
            }
        }
        if (displayName != null) {
            setDisplayName(displayName);
            setSynchronizeName(false);
            renameable = false;
        }
    }

    boolean renameable = true;

    @Override public boolean canCopy() {
        return false;
    }
    @Override public boolean canRename() {
        return renameable;
    }
    @Override public Action[] getActions(boolean context) {
        Object o = getBean();
        if (o instanceof ActionableBean) {
            return ((ActionableBean)o).actions();
        } else {
            return null;
        }
    }

    public interface ActionableBean {
        Action[] actions();
    }
}
