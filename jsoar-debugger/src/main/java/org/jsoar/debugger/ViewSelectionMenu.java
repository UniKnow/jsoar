/*
 * Copyright (c) 2010 Dave Ray <daveray@gmail.com>
 *
 * Created on Jul 31, 2010
 */
package org.jsoar.debugger;

import bibliothek.gui.DockFrontend;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.event.DockFrontendAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** @author ray */
public class ViewSelectionMenu {
  private final CControl control;
  private final JMenu menu;

  /** @param control */
  public ViewSelectionMenu(CControl control, JMenu menu) {
    this.control = control;
    this.menu = menu;

    this.control
        .intern()
        .addFrontendListener(
            new DockFrontendAdapter() {

              @Override
              public void added(DockFrontend frontend, Dockable dockable) {
                addMenu(dockable);
              }

              // TODO handle removal of views...
            });

    for (Dockable dockable : control.intern().listDockables()) {
      addMenu(dockable);
    }
  }

  private void addMenu(Dockable dockable) {
    final AbstractAdaptableView view = AbstractAdaptableView.fromDockable(dockable);
    if (view != null) {
      final JMenuItem item = new JMenuItem(view.getTitleText());
      final String ks = view.getShortcutKey();
      if (ks != null) {
        item.setAccelerator(KeyStroke.getKeyStroke(ks));
      }

      item.addActionListener(
          new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
              view.setVisible(true);
              view.toFront();
              view.activate();
              if (view instanceof Refreshable) {
                ((Refreshable) view).refresh(false);
              }
            }
          });
      menu.insert(item, 0);
    }
  }
}
