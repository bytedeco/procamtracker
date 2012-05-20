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

import com.googlecode.javacpp.Pointer;
import com.googlecode.javacv.CameraDevice;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.GNImageAligner;
import com.googlecode.javacv.HandMouse;
import com.googlecode.javacv.JavaCvErrorCallback;
import com.googlecode.javacv.MarkerDetector;
import com.googlecode.javacv.ObjectFinder;
import com.googlecode.javacv.ProjectorDevice;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.beans.PropertyVetoException;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.media.opengl.GLProfile;
import javax.media.opengl.Threading;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker.StateValue;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.DefaultEditorKit;
import org.netbeans.beaninfo.editors.StringArrayEditor;
import org.netbeans.core.output2.NbIOProvider;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.propertysheet.PropertySheetView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.windows.IOContainer;
import org.openide.windows.InputOutput;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;


/**
 *
 * @author Samuel Audet
 *
 * Libraries needed from the directory of NetBeans:
 *   boot.jar
 *   core.jar
 *   org-netbeans-core.jar
 *   org-netbeans-core-output2.jar
 *   org-netbeans-swing-plaf.jar
 *   org-openide-actions.jar
 *   org-openide-awt.jar
 *   org-openide-dialogs.jar
 *   org-openide-explorer.jar
 *   org-openide-filesystems.jar
 *   org-openide-io.jar
 *   org-openide-modules.jar
 *   org-openide-nodes.jar
 *   org-openide-util.jar
 *   org-openide-util-lookup.jar
 */
public class MainFrame extends javax.swing.JFrame implements
        ExplorerManager.Provider, Lookup.Provider, PropertyChangeListener {

    /** Creates new form MainFrame */
    public MainFrame(String[] args) throws Exception {
        // same as before...
        manager = new ExplorerManager();
        ActionMap map = getRootPane().getActionMap();
        map.put(DefaultEditorKit.copyAction, ExplorerUtils.actionCopy(manager));
        map.put(DefaultEditorKit.cutAction, ExplorerUtils.actionCut(manager));
        map.put(DefaultEditorKit.pasteAction, ExplorerUtils.actionPaste(manager));
        map.put("delete", ExplorerUtils.actionDelete(manager, true)); // or false

        // ...but add e.g.:
        InputMap keys = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        keys.put(KeyStroke.getKeyStroke("control C"), DefaultEditorKit.copyAction);
        keys.put(KeyStroke.getKeyStroke("control X"), DefaultEditorKit.cutAction);
        keys.put(KeyStroke.getKeyStroke("control V"), DefaultEditorKit.pasteAction);
        keys.put(KeyStroke.getKeyStroke("DELETE"), "delete");

        // ...and initialization of lookup variable
        lookup = ExplorerUtils.createLookup(manager, map);


        settingsFile = args.length > 0 ? new File(args[0]) : null;
        try {
            Logger.getLogger("").addHandler(new Handler() {
                {
                    setFormatter(new SimpleFormatter());
                }
                @Override public void publish(final LogRecord record) {
                    final String title;
                    final int messageType;
                    if (record.getLevel().equals(Level.SEVERE)) {
                        title = "SEVERE Logging Message";
                        messageType = JOptionPane.ERROR_MESSAGE;
                    } else if (record.getLevel().equals(Level.WARNING)) {
                        title = "WARNING Logging Message";
                        messageType = JOptionPane.WARNING_MESSAGE;
                    } else if (record.getLevel().equals(Level.INFO)) {
                        title = "INFO Logging Message";
                        messageType = JOptionPane.INFORMATION_MESSAGE;
                    } else {
                        title = "Tracing Logging Message";
                        messageType = JOptionPane.PLAIN_MESSAGE;
                    }
                    String[] messageLines = getFormatter().format(record).split("\r\n|\r|\n");
                    StringBuilder messageBuilder = new StringBuilder();
                    for (int i = 0; i < Math.min(5, messageLines.length); i++) {
                        messageBuilder.append(messageLines[i] + '\n');
                    }
                    if (messageLines.length > 5) {
                        messageBuilder.append("...");
                    }
                    final String message = messageBuilder.toString();

                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            if (messageType == JOptionPane.INFORMATION_MESSAGE) {
                                messagesio.getOut().println(getFormatter().formatMessage(record));
                            } else {
                                JOptionPane.showMessageDialog(MainFrame.this,
                                        message, title, messageType);
                            }
                        }
                    });
                }
                @Override public void flush() { }
                @Override public void close() throws SecurityException { }
            });

            cvRedirectError(new JavaCvErrorCallback() {
                @Override public int call(int status, String func_name, String err_msg,
                        String file_name, int line, Pointer userdata) {
                    super.call(status, func_name, err_msg, file_name, line, userdata);
                    if (trackingWorker != null) {
                        trackingWorker.cancel();
                    }
                    return 0; // please don't terminate
                }
            }, null, null);

            initComponents();
            loadSettings(settingsFile);
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                    "Could not load settings from \"" + settingsFile + "\"", ex);
            loadSettings(null);
        } catch (LinkageError e) {
            throw new Exception(e);
        }

        // initialize the messages window
        IOContainer container = IOContainer.getDefault();
        Field f = container.getClass().getDeclaredField("provider");
        f.setAccessible(true);
        IOContainer.Provider prov = (IOContainer.Provider)f.get(container);
        f = prov.getClass().getDeclaredField("frame");
        f.setAccessible(true);
        f.set(prov, this);
        verticalSplitPane.setRightComponent((JComponent)prov);

        beanTreeView.requestFocusInWindow();
    }

    CameraDevice    .Settings cameraSettings;
    ProjectorDevice .Settings projectorSettings;
    ObjectFinder    .Settings objectFinderSettings;
    MarkerDetector  .Settings markerDetectorSettings;
    GNImageAligner  .Settings alignerSettings;
    HandMouse       .Settings handMouseSettings;
    VirtualBall     .Settings virtualBallSettings;
    RealityAugmentor.Settings realityAugmentorSettings;
    TrackingWorker  .Settings trackingSettings;
    final File DEFAULT_SETTINGS_FILE = new File("settings.pct");
    File settingsFile = null;

    private ExplorerManager manager;
    private Lookup lookup;

    private InputOutput messagesio;

    // ...method as before and getLookup
    public ExplorerManager getExplorerManager() {
        return manager;
    }
    public Lookup getLookup() {
        return lookup;
    }
    // ...methods as before, but replace componentActivated and
    // componentDeactivated with e.g.:
    @Override public void addNotify() {
        super.addNotify();
        ExplorerUtils.activateActions(manager, true);
    }
    @Override public void removeNotify() {
        ExplorerUtils.activateActions(manager, false);
        super.removeNotify();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == trackingWorker && evt.getPropertyName().equals("progress")) {
            switch ((Integer)evt.getNewValue()) {
                case TrackingWorker.INITIALIZING: statusLabel.setText("Initializing..."); break;
                case TrackingWorker.TRACKING:     statusLabel.setText("Tracking...");     break;
                default: assert (false);
            }
        }
    }

    void buildSettingsView() throws IntrospectionException, PropertyVetoException {
        HashMap<String, Class<? extends PropertyEditor>> editors =
                new HashMap<String, Class<? extends PropertyEditor>>();
        editors.put("frameGrabber", FrameGrabber.PropertyEditor.class);

        // hide settings we do not need from the user...
        editors.put("triggerMode", null);
        editors.put("imageMode", null);
        editors.put("timeout", null);
        editors.put("parametersFilename", null);
        editors.put("deviceFilename", null);
        editors.put("useOpenGL", null);
        editors.put("objectImage", null);
        editors.put("gammaTgamma", null);
        editors.put("outputVideoFilename", null);
        editors.put("textureImageFilename", null);
        editors.put("projectorImageFilename", null);
        editors.put("projectorVideoFilename", null);
        editors.put("initialRoiPts", null);
        editors.put("initialPosition", null);

        if (cameraSettings == null) {
            cameraSettings = new CameraDevice.CalibratedSettings();
            cameraSettings.setFrameGrabber(FrameGrabber.getDefault());
        }
        cameraSettings.addPropertyChangeListener(this);
        BeanNode cameraNode = new CleanBeanNode<CameraDevice.Settings>
                (cameraSettings, editors, "Camera");

        if (projectorSettings == null) {
            projectorSettings = new ProjectorDevice.CalibratedSettings();
        }
        projectorSettings.addPropertyChangeListener(this);
        BeanNode projectorNode = new CleanBeanNode<ProjectorDevice.Settings>
                (projectorSettings, editors, "Projector");

        if (objectFinderSettings == null) {
            objectFinderSettings = new ObjectFinder.Settings();
        }
        objectFinderSettings.addPropertyChangeListener(this);
        BeanNode objectFinderNode = new CleanBeanNode<ObjectFinder.Settings>
                (objectFinderSettings, editors, "ObjectFinder");

        if (markerDetectorSettings == null) {
            markerDetectorSettings = new MarkerDetector.Settings();
        }
        markerDetectorSettings.addPropertyChangeListener(this);
        BeanNode markerDetectorNode = new CleanBeanNode<MarkerDetector.Settings>
                (markerDetectorSettings, editors, "MarkerDetector");

        if (alignerSettings == null) {
            alignerSettings = new GNImageAligner.Settings();
        }
        BeanNode alignerNode = new CleanBeanNode<GNImageAligner.Settings>
                (alignerSettings, editors, "GNImageAligner");

        if (handMouseSettings == null) {
            handMouseSettings = new HandMouse.Settings();
        }
        BeanNode handMouseNode = new CleanBeanNode<HandMouse.Settings>
                (handMouseSettings, editors, "HandMouse");

        if (virtualBallSettings == null) {
            virtualBallSettings = new VirtualBall.Settings();
        }
        BeanNode virtualBallNode = new CleanBeanNode<VirtualBall.Settings>
                (virtualBallSettings, editors, "VirtualBall");

        if (realityAugmentorSettings == null) {
            realityAugmentorSettings = new RealityAugmentor.Settings();
            RealityAugmentor.ObjectSettings os = new RealityAugmentor.ObjectSettings();
            RealityAugmentor.VirtualSettings vs = new RealityAugmentor.VirtualSettings();
            os.add(vs);
            realityAugmentorSettings.add(os);
        }
        BeanNode realityAugmentorNode = new CleanBeanNode<RealityAugmentor.Settings>
                (realityAugmentorSettings, editors, "RealityAugmentor");

        if (trackingSettings == null) {
            trackingSettings = new TrackingWorker.Settings();
        }
        BeanNode trackingNode = new CleanBeanNode<TrackingWorker.Settings>
                (trackingSettings, editors, "TrackingWorker");

        Children children = new Children.Array();
        children.add(new Node[] { cameraNode, projectorNode, objectFinderNode,
                markerDetectorNode, alignerNode, handMouseNode, virtualBallNode,
                realityAugmentorNode, trackingNode });

        Node root = new AbstractNode(children);
        root.setName("Settings");
        manager.setRootContext(root);
    }

    void loadSettings(File file) throws IOException, IntrospectionException, PropertyVetoException {
        if (file == null) {
            cameraSettings = null;
            projectorSettings = null;
            objectFinderSettings = null;
            markerDetectorSettings = null;
            alignerSettings = null;
            handMouseSettings = null;
            virtualBallSettings = null;
            realityAugmentorSettings = null;
            trackingSettings = null;

            trackingWorker = null;
        } else {
            XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(file)));
            cameraSettings = (CameraDevice.Settings)decoder.readObject();
            projectorSettings = (ProjectorDevice.Settings)decoder.readObject();
            objectFinderSettings = (ObjectFinder.Settings)decoder.readObject();
            markerDetectorSettings = (MarkerDetector.Settings)decoder.readObject();
            alignerSettings = (GNImageAligner.Settings)decoder.readObject();
            handMouseSettings = (HandMouse.Settings)decoder.readObject();
            virtualBallSettings = (VirtualBall.Settings)decoder.readObject();
            realityAugmentorSettings = (RealityAugmentor.Settings)decoder.readObject();
            trackingSettings = (TrackingWorker.Settings)decoder.readObject();
            decoder.close();
        }

        settingsFile = file;
        if (settingsFile == null) {
            setTitle("ProCamTracker");
        } else {
            setTitle(settingsFile.getName() + " - ProCamTracker");
        }

        buildSettingsView();

        if (trackingWorker == null) {
            statusLabel.setText("Idling.");
        }
    }

    void saveSettings(File file) throws IOException {
        settingsFile = file;
        if (settingsFile == null) {
            setTitle("ProCamTracker");
        } else {
            setTitle(settingsFile.getName() + " - ProCamTracker");
        }

        XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(settingsFile)));
        encoder.writeObject(cameraSettings);
        encoder.writeObject(projectorSettings);
        encoder.writeObject(objectFinderSettings);
        encoder.writeObject(markerDetectorSettings);
        encoder.writeObject(alignerSettings);
        encoder.writeObject(handMouseSettings);
        encoder.writeObject(virtualBallSettings);
        encoder.writeObject(realityAugmentorSettings);
        encoder.writeObject(trackingSettings);
        encoder.close();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        toolBar = new javax.swing.JToolBar();
        settingsLoadDefaultsButton = new javax.swing.JButton();
        settingsLoadButton = new javax.swing.JButton();
        settingsSaveButton = new javax.swing.JButton();
        toolBarSeparator1 = new javax.swing.JToolBar.Separator();
        trackingStartButton = new javax.swing.JButton();
        trackingStopButton = new javax.swing.JButton();
        verticalSplitPane = new javax.swing.JSplitPane();
        horizontalSplitPane = new javax.swing.JSplitPane();
        beanTreeView = new org.openide.explorer.view.BeanTreeView();
        propertySheetView = new org.openide.explorer.propertysheet.PropertySheetView();
        statusLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        settingsMenu = new javax.swing.JMenu();
        settingsLoadDefaultsMenuItem = new javax.swing.JMenuItem();
        settingsLoadMenuItem = new javax.swing.JMenuItem();
        settingsSaveMenuItem = new javax.swing.JMenuItem();
        settingsSaveAsMenuItem = new javax.swing.JMenuItem();
        trackingMenu = new javax.swing.JMenu();
        trackingStartMenuItem = new javax.swing.JMenuItem();
        trackingStopMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        readmeMenuItem = new javax.swing.JMenuItem();
        menuSeparator2 = new javax.swing.JSeparator();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ProCamTracker");

        toolBar.setFloatable(false);
        toolBar.setRollover(true);

        settingsLoadDefaultsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/googlecode/javacv/procamtracker/icons/cleanCurrentProject.gif"))); // NOI18N
        settingsLoadDefaultsButton.setToolTipText("Load Defaults");
        settingsLoadDefaultsButton.setFocusable(false);
        settingsLoadDefaultsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        settingsLoadDefaultsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        settingsLoadDefaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsLoadDefaultsButtonActionPerformed(evt);
            }
        });
        toolBar.add(settingsLoadDefaultsButton);

        settingsLoadButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/googlecode/javacv/procamtracker/icons/openProject.png"))); // NOI18N
        settingsLoadButton.setToolTipText("Load Settings");
        settingsLoadButton.setFocusable(false);
        settingsLoadButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        settingsLoadButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        settingsLoadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsLoadButtonActionPerformed(evt);
            }
        });
        toolBar.add(settingsLoadButton);

        settingsSaveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/googlecode/javacv/procamtracker/icons/save.png"))); // NOI18N
        settingsSaveButton.setToolTipText("Save Settings");
        settingsSaveButton.setFocusable(false);
        settingsSaveButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        settingsSaveButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        settingsSaveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsSaveButtonActionPerformed(evt);
            }
        });
        toolBar.add(settingsSaveButton);
        toolBar.add(toolBarSeparator1);

        trackingStartButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/googlecode/javacv/procamtracker/icons/runProject.png"))); // NOI18N
        trackingStartButton.setToolTipText("Start Tracking");
        trackingStartButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackingStartButtonActionPerformed(evt);
            }
        });
        toolBar.add(trackingStartButton);

        trackingStopButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/googlecode/javacv/procamtracker/icons/stop.png"))); // NOI18N
        trackingStopButton.setToolTipText("Stop Tracking");
        trackingStopButton.setEnabled(false);
        trackingStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackingStopButtonActionPerformed(evt);
            }
        });
        toolBar.add(trackingStopButton);

        verticalSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        verticalSplitPane.setResizeWeight(0.6);

        horizontalSplitPane.setResizeWeight(0.5);

        beanTreeView.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        horizontalSplitPane.setLeftComponent(beanTreeView);

        propertySheetView.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        propertySheetView.setDescriptionAreaVisible(false);
        try {
            propertySheetView.setSortingMode(PropertySheetView.SORTED_BY_NAMES);
        } catch (java.beans.PropertyVetoException e1) {
            e1.printStackTrace();
        }
        horizontalSplitPane.setRightComponent(propertySheetView);

        verticalSplitPane.setLeftComponent(horizontalSplitPane);

        statusLabel.setText("Status");
        statusLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 5, 5));

        settingsMenu.setMnemonic('E');
        settingsMenu.setText("Settings");

        settingsLoadDefaultsMenuItem.setMnemonic('D');
        settingsLoadDefaultsMenuItem.setText("Load Defaults");
        settingsLoadDefaultsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsLoadDefaultsMenuItemActionPerformed(evt);
            }
        });
        settingsMenu.add(settingsLoadDefaultsMenuItem);

        settingsLoadMenuItem.setMnemonic('L');
        settingsLoadMenuItem.setText("Load...");
        settingsLoadMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsLoadMenuItemActionPerformed(evt);
            }
        });
        settingsMenu.add(settingsLoadMenuItem);

        settingsSaveMenuItem.setMnemonic('S');
        settingsSaveMenuItem.setText("Save");
        settingsSaveMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsSaveMenuItemActionPerformed(evt);
            }
        });
        settingsMenu.add(settingsSaveMenuItem);

        settingsSaveAsMenuItem.setMnemonic('A');
        settingsSaveAsMenuItem.setText("Save As...");
        settingsSaveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsSaveAsMenuItemActionPerformed(evt);
            }
        });
        settingsMenu.add(settingsSaveAsMenuItem);

        menuBar.add(settingsMenu);

        trackingMenu.setMnemonic('T');
        trackingMenu.setText("Tracking");

        trackingStartMenuItem.setMnemonic('T');
        trackingStartMenuItem.setText("Start     ");
        trackingStartMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackingStartMenuItemActionPerformed(evt);
            }
        });
        trackingMenu.add(trackingStartMenuItem);

        trackingStopMenuItem.setMnemonic('O');
        trackingStopMenuItem.setText("Stop      ");
        trackingStopMenuItem.setEnabled(false);
        trackingStopMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackingStopMenuItemActionPerformed(evt);
            }
        });
        trackingMenu.add(trackingStopMenuItem);

        menuBar.add(trackingMenu);

        helpMenu.setMnemonic('H');
        helpMenu.setText("Help");

        readmeMenuItem.setMnemonic('R');
        readmeMenuItem.setText("README");
        readmeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                readmeMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(readmeMenuItem);
        helpMenu.add(menuSeparator2);

        aboutMenuItem.setMnemonic('A');
        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toolBar, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
            .addComponent(verticalSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
            .addComponent(statusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(verticalSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusLabel))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void settingsLoadDefaultsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsLoadDefaultsMenuItemActionPerformed
        if (evt != null) {
            int response = JOptionPane.showConfirmDialog(this,
                    "Load defaults settings and lose current ones?", "Confirm Reset",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        try {
            loadSettings(null);
        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_settingsLoadDefaultsMenuItemActionPerformed

    private void settingsLoadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsLoadMenuItemActionPerformed
        JFileChooser fc = new JFileChooser();
        if (settingsFile != null) {
            fc.setSelectedFile(settingsFile);
        } else {
            fc.setSelectedFile(DEFAULT_SETTINGS_FILE);
        }
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                loadSettings(file);
            } catch (Exception ex) {
                Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                        "Could not load settings from \"" + file + "\"", ex);
            }
        }

    }//GEN-LAST:event_settingsLoadMenuItemActionPerformed

    private void settingsSaveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsSaveMenuItemActionPerformed
        if (settingsFile == null) {
            settingsSaveAsMenuItemActionPerformed(evt);
        } else {
            if (settingsFile.exists()) {
                int response = JOptionPane.showConfirmDialog(this,
                        "Overwrite existing file \"" + settingsFile + "\"?", "Confirm Overwrite",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (response == JOptionPane.CANCEL_OPTION) {
                    settingsSaveAsMenuItemActionPerformed(evt);
                    return;
                }
            }

            try {
                saveSettings(settingsFile);
            } catch (IOException ex) {
                Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                        "Could not save settings to \"" + settingsFile + "\"", ex);
            }
        }
    }//GEN-LAST:event_settingsSaveMenuItemActionPerformed

    private void settingsSaveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsSaveAsMenuItemActionPerformed
        JFileChooser fc = new JFileChooser();
        if (settingsFile != null) {
            fc.setSelectedFile(settingsFile);
        } else {
            fc.setSelectedFile(DEFAULT_SETTINGS_FILE);
        }
        int returnVal = fc.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            settingsFile = fc.getSelectedFile();
            settingsSaveMenuItemActionPerformed(evt);
        }

    }//GEN-LAST:event_settingsSaveAsMenuItemActionPerformed

    class MyTrackingWorker extends TrackingWorker {
        public MyTrackingWorker(MyTrackingWorker brother) {
            super();
            if (brother != null) {
                this.cameraDevice = brother.cameraDevice;
                this.projectorDevice = brother.projectorDevice;
            }
        }

        @Override protected void done() {
            super.done();

            settingsMenu.setEnabled(true);
//            trackingMenu.setEnabled(true);
            trackingStartMenuItem.setEnabled(true);
            trackingStopMenuItem.setEnabled(false);
            settingsLoadDefaultsButton.setEnabled(true);
            settingsLoadButton.setEnabled(true);
            settingsSaveButton.setEnabled(true);
            trackingStartButton.setEnabled(true);
            trackingStopButton.setEnabled(false);
            beanTreeView.setEnabled(true);
            propertySheetView.setEnabled(true);

            if (isCancelled()) {
                statusLabel.setText("Tracking stopped.");
                trackingWorker = null;
            } else {
                statusLabel.setText("Tracking done.");
            }
        }
    }
    MyTrackingWorker trackingWorker = null;

    private void trackingStartMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackingStartMenuItemActionPerformed
        if (trackingWorker == null || trackingWorker.getState() != StateValue.STARTED) {
            trackingWorker = new MyTrackingWorker(trackingWorker);
            trackingWorker.addPropertyChangeListener(this);

            settingsMenu.setEnabled(false);
//            trackingMenu.setEnabled(false);
            trackingStartMenuItem.setEnabled(false);
            trackingStopMenuItem.setEnabled(true);
            settingsLoadDefaultsButton.setEnabled(false);
            settingsLoadButton.setEnabled(false);
            settingsSaveButton.setEnabled(false);
            trackingStartButton.setEnabled(false);
            trackingStopButton.setEnabled(true);
            beanTreeView.setEnabled(false);
            propertySheetView.setEnabled(false);

            try {
                // PropertySheetView doesn't actually disable, so let's
                // select the dummy root node...
                manager.setSelectedNodes(new Node[]{manager.getRootContext()});
            } catch (Exception ex) { }

            trackingWorker.cameraSettings = cameraSettings;
            trackingWorker.projectorSettings = projectorSettings;
            trackingWorker.objectFinderSettings = objectFinderSettings;
            trackingWorker.markerDetectorSettings = markerDetectorSettings;
            trackingWorker.alignerSettings = alignerSettings;
            trackingWorker.handMouseSettings = handMouseSettings;
            trackingWorker.virtualBallSettings = virtualBallSettings;
            trackingWorker.realityAugmentorSettings = realityAugmentorSettings;
            trackingWorker.trackingSettings = trackingSettings;
            try {
                trackingWorker.init();
                trackingWorker.execute();

                //statusLabel.setText("Tracking...");
            } catch (Exception ex) {
                Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                        "Could not initialize tracking worker thread.", ex);
                trackingWorker.cancel();
            }
        } else {
            assert(false);
        }
    }//GEN-LAST:event_trackingStartMenuItemActionPerformed

    private void trackingStopMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackingStopMenuItemActionPerformed
        if (trackingWorker != null) {
            trackingWorker.cancel();
        } else {
            assert(false);
        }
    }//GEN-LAST:event_trackingStopMenuItemActionPerformed

    private void readmeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_readmeMenuItemActionPerformed
        try {
            JTextArea textArea = new JTextArea();
            Font font = textArea.getFont();
            textArea.setFont(new Font("Monospaced", font.getStyle(), font.getSize()));
            textArea.setEditable(false);

            String text = "";
            BufferedReader r = new BufferedReader(new FileReader(
                    myDirectory + File.separator + "../README.txt"));
            String line;
            while ((line = r.readLine()) != null) {
                text += line + '\n';
            }

            textArea.setText(text);
            textArea.setCaretPosition(0);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setColumns(80);

            // stuff it in a scrollpane with a controlled size.
            JScrollPane scrollPane = new JScrollPane(textArea);
            Dimension dim = textArea.getPreferredSize();
            dim.height = dim.width*50/80;
            scrollPane.setPreferredSize(dim);

            // pass the scrollpane to the joptionpane.
            JDialog dialog = new JOptionPane(scrollPane, JOptionPane.PLAIN_MESSAGE).
                    createDialog(this, "README");
            dialog.setResizable(true);
            dialog.setModalityType(ModalityType.MODELESS);
            dialog.setVisible(true);
        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_readmeMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        String timestamp = MainFrame.class.getPackage().getImplementationVersion();
        if (timestamp == null) {
            timestamp = "unknown";
        }
//        String timestamp = "unknown";
//        try {
//            URL u = MainFrame.class.getClassLoader().getResource("com/googlecode/javacv/procamtracker");
//            JarURLConnection c = (JarURLConnection)u.openConnection();
//            timestamp = c.getManifest().getMainAttributes().getValue("Time-Stamp");
//        } catch (Exception e) { }

        JTextPane textPane = new JTextPane();
	textPane.setEditable(false);
        textPane.setContentType("text/html");
        textPane.setText(
                "<font face=sans-serif><strong><font size=+2>ProCamTracker</font></strong><br>" +
                "build timestamp " + timestamp + "<br>" +
                "Copyright (C) 2009-2012 Samuel Audet &lt;<a href=\"mailto:saudet@ok.ctrl.titech.ac.jp%28Samuel%20Audet%29\">saudet@ok.ctrl.titech.ac.jp</a>&gt;<br>" +
                "Web site: <a href=\"http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/\">http://www.ok.ctrl.titech.ac.jp/~saudet/procamtracker/</a><br>" +
                "<br>" +
                "Licensed under the GNU General Public License version 2 (GPLv2).<br>" +
                "Please refer to LICENSE.txt or <a href=\"http://www.gnu.org/licenses/\">http://www.gnu.org/licenses/</a> for details."
                );
        textPane.setCaretPosition(0);
        Dimension dim = textPane.getPreferredSize();
        dim.height = dim.width*3/4;
        textPane.setPreferredSize(dim);

        textPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if(e.getEventType() == EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch(Exception ex) {
                        Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                                "Could not launch browser to \"" + e.getURL()+ "\"", ex);
                    }
                }
            }
        });

        // pass the scrollpane to the joptionpane.
        JDialog dialog = new JOptionPane(textPane, JOptionPane.PLAIN_MESSAGE).
                createDialog(this, "About");

        if (UIManager.getLookAndFeel().getClass().getName()
                .equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
            // under GTK, frameBackground is white, but rootPane color is OK...
            // but under Windows, the rootPane's color is funny...
            Color c = dialog.getRootPane().getBackground();
            textPane.setBackground(new Color(c.getRGB()));
        } else {
            Color frameBackground = this.getBackground();
            textPane.setBackground(frameBackground);
        }
        dialog.setVisible(true);
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void settingsLoadDefaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsLoadDefaultsButtonActionPerformed
        settingsLoadDefaultsMenuItemActionPerformed(evt);
    }//GEN-LAST:event_settingsLoadDefaultsButtonActionPerformed

    private void settingsLoadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsLoadButtonActionPerformed
        settingsLoadMenuItemActionPerformed(evt);
    }//GEN-LAST:event_settingsLoadButtonActionPerformed

    private void settingsSaveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsSaveButtonActionPerformed
        settingsSaveMenuItemActionPerformed(evt);
    }//GEN-LAST:event_settingsSaveButtonActionPerformed

    private void trackingStartButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackingStartButtonActionPerformed
        trackingStartMenuItemActionPerformed(evt);
    }//GEN-LAST:event_trackingStartButtonActionPerformed

    private void trackingStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackingStopButtonActionPerformed
        trackingStopMenuItemActionPerformed(evt);
    }//GEN-LAST:event_trackingStopButtonActionPerformed

    static File myDirectory;

    /**
    * @param args the command line arguments
    */
    public static void main(final String args[]) {
        try {
            Threading.disableSingleThreading();
            //System.setProperty("sun.java2d.opengl","false");
            GLProfile.initSingleton();
        } catch (Throwable t) { }

        // try to init all frame grabbers here, because bad things
        // happen if loading errors occur while we're in the GUI thread...
        FrameGrabber.init();

        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    myDirectory = new File(MainFrame.class.getProtectionDomain().
                            getCodeSource().getLocation().toURI());
                    if (!myDirectory.isDirectory()) {
                        myDirectory = myDirectory.getParentFile();
                    }
                    String path = myDirectory.getAbsolutePath();

                    String lafClassName = UIManager.getSystemLookAndFeelClassName();
                    ArrayList<String> otherArgs = new ArrayList<String>();
                    for (int i = 0; i < args.length; i++) {
                        if (args[i].equals("--laf") && i+1 < args.length) {
                            lafClassName = args[i+1];
                            i++;
                        } else {
                            otherArgs.add(args[i]);
                        }
                    }
                    // "Ocean Look" would be javax.swing.plaf.metal.MetalLookAndFeel
                    org.netbeans.swing.plaf.Startup.run(Class.forName(lafClassName), 0, null);

                    // Add property editors from NetBeans
                    String[] searchPath = PropertyEditorManager.getEditorSearchPath();
                    String[] newSearchPath = new String[searchPath.length+1];
                    newSearchPath[0] = "org.netbeans.beaninfo.editors";
                    System.arraycopy(searchPath, 0, newSearchPath, 1, searchPath.length);
                    PropertyEditorManager.setEditorSearchPath(newSearchPath);
                    PropertyEditorManager.registerEditor(String[].class, StringArrayEditor.class);
                    PropertyEditorManager.registerEditor(double[].class, DoubleArrayEditor.class);

                    //Make sure we have nice window decorations.
                    JFrame.setDefaultLookAndFeelDecorated(true);
                    JDialog.setDefaultLookAndFeelDecorated(true);

                    MainFrame w = new MainFrame(otherArgs.toArray(new String[0]));
                    w.setLocationByPlatform(true);
                    w.setVisible(true);

                    w.messagesio = new NbIOProvider().getIO("Messages", new Action[0],
                            IOContainer.getDefault());
                    w.messagesio.select();
                } catch (Exception ex) {
                    Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
                            "Could not start ProCamTracker", ex);
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private org.openide.explorer.view.BeanTreeView beanTreeView;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JSplitPane horizontalSplitPane;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JSeparator menuSeparator2;
    private org.openide.explorer.propertysheet.PropertySheetView propertySheetView;
    private javax.swing.JMenuItem readmeMenuItem;
    private javax.swing.JButton settingsLoadButton;
    private javax.swing.JButton settingsLoadDefaultsButton;
    private javax.swing.JMenuItem settingsLoadDefaultsMenuItem;
    private javax.swing.JMenuItem settingsLoadMenuItem;
    private javax.swing.JMenu settingsMenu;
    private javax.swing.JMenuItem settingsSaveAsMenuItem;
    private javax.swing.JButton settingsSaveButton;
    private javax.swing.JMenuItem settingsSaveMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JToolBar toolBar;
    private javax.swing.JToolBar.Separator toolBarSeparator1;
    private javax.swing.JMenu trackingMenu;
    private javax.swing.JButton trackingStartButton;
    private javax.swing.JMenuItem trackingStartMenuItem;
    private javax.swing.JButton trackingStopButton;
    private javax.swing.JMenuItem trackingStopMenuItem;
    private javax.swing.JSplitPane verticalSplitPane;
    // End of variables declaration//GEN-END:variables

}
