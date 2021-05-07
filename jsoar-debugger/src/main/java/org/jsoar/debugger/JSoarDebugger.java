/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 21, 2008
 */
package org.jsoar.debugger;

import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.MultipleCDockable;
import bibliothek.gui.dock.common.event.CFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.menu.CLayoutChoiceMenuPiece;
import bibliothek.gui.dock.common.menu.CThemeMenuPiece;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.facile.menu.RootMenuPiece;
import bibliothek.gui.dock.facile.menu.SubmenuPiece;
import bibliothek.gui.dock.support.menu.SeparatingMenuPiece;
import bibliothek.util.xml.XElement;
import bibliothek.util.xml.XIO;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.jdesktop.swingx.JXFrame;
import org.jsoar.debugger.actions.AboutAction;
import org.jsoar.debugger.actions.ActionManager;
import org.jsoar.debugger.actions.EditProductionAction;
import org.jsoar.debugger.actions.ExciseProductionAction;
import org.jsoar.debugger.actions.ExitAction;
import org.jsoar.debugger.actions.GarbageCollectorAction;
import org.jsoar.debugger.actions.InitSoarAction;
import org.jsoar.debugger.actions.MatchesProductionAction;
import org.jsoar.debugger.actions.RestoreLayoutAction;
import org.jsoar.debugger.actions.RunAction;
import org.jsoar.debugger.actions.SetBreakpointAction;
import org.jsoar.debugger.actions.SourceFileAction;
import org.jsoar.debugger.actions.StepAction;
import org.jsoar.debugger.actions.StopAction;
import org.jsoar.debugger.actions.UrlAction;
import org.jsoar.debugger.selection.SelectionManager;
import org.jsoar.debugger.selection.SelectionProvider;
import org.jsoar.debugger.stopcommand.StopCommandAction;
import org.jsoar.debugger.stopcommand.StopCommandView;
import org.jsoar.debugger.stopcommand.StopCommandViewFactory;
import org.jsoar.debugger.util.Prefs;
import org.jsoar.debugger.util.PrefsFactory;
import org.jsoar.debugger.util.SwingTools;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.DebuggerProvider;
import org.jsoar.kernel.DebuggerProvider.CloseAction;
import org.jsoar.kernel.RunType;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.events.AfterInitSoarEvent;
import org.jsoar.kernel.events.StopEvent;
import org.jsoar.runtime.CompletionHandler;
import org.jsoar.runtime.SwingCompletionHandler;
import org.jsoar.runtime.ThreadedAgent;
import org.jsoar.util.adaptables.Adaptable;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.commands.SoarCommand;
import org.jsoar.util.commands.SoarCommandInterpreter;
import org.jsoar.util.commands.SoarCommands;
import org.jsoar.util.events.SoarEvent;
import org.jsoar.util.events.SoarEventListener;
import org.jsoar.util.properties.PropertyChangeEvent;
import org.jsoar.util.properties.PropertyKey;
import org.jsoar.util.properties.PropertyListener;
import org.jsoar.util.properties.PropertyListenerHandle;
import org.jsoar.util.properties.PropertyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author ray */
public class JSoarDebugger extends JPanel implements Adaptable {
  private static final long serialVersionUID = 7997119112479665988L;

  private static final Logger logger = LoggerFactory.getLogger(JSoarDebugger.class);

  private static final ResourceBundle resources = ResourceBundle.getBundle("jsoar");
  private static Preferences PREFERENCES;
  private static final PropertyKey<JSoarDebugger> CREATED_BY =
      PropertyKey.builder("JSoarDebugger.createdBy", JSoarDebugger.class).readonly(true).build();

  private static final Map<ThreadedAgent, JSoarDebugger> debuggers =
      Collections.synchronizedMap(new HashMap<ThreadedAgent, JSoarDebugger>());

  private final SelectionManager selectionManager = new SelectionManager();
  private final ActionManager actionManager = new ActionManager(this);
  private final RunControlModel runControlModel = new RunControlModel();

  private Map<String, Object> providerProperties = new HashMap<String, Object>();
  private boolean resetPreferencesAtExit = false;

  private ThreadedAgent agent;
  private final LoadPluginCommand loadPluginCommand = new LoadPluginCommand(this);
  private final List<JSoarDebuggerPlugin> plugins = new CopyOnWriteArrayList<JSoarDebuggerPlugin>();

  public JFrame frame;
  private CControl docking;
  private StatusBar status;

  private final List<CDockable> views = new ArrayList<>();

  private final List<SoarEventListener> soarEventListeners = new ArrayList<SoarEventListener>();
  private final List<PropertyListenerHandle<?>> propertyListeners =
      new ArrayList<PropertyListenerHandle<?>>();
  private static float fontScale = 1.0f;
  private StopCommandViewFactory factory;

  /** Construct a new debugger. Add to a JFrame and call initialize(). */
  private JSoarDebugger(Map<String, Object> properties) {
    super(new BorderLayout());

    this.providerProperties.putAll(properties);
  }

  public static float getFontScale() {
    return fontScale;
  }

  /**
   * Initialize the debugger
   *
   * @param parentFrame The parent frame of the debugger
   * @param proxy A non-null, <b>initialized</b> agent proxy
   */
  private void initialize(final JXFrame parentFrame, ThreadedAgent proxy) {
    logger.info("Initializing debugger for agent '" + proxy + "'");
    final float scaleUpFactor = 4.0f / 3.0f;
    final float scaleDownFactor = .75f;
    parentFrame.addMouseWheelListener(
        e -> {
          if (e.isControlDown()) {
            float scaleFactor = 1.0f;
            if (e.getWheelRotation() < 0) {
              scaleFactor = scaleUpFactor;
            } else if (e.getWheelRotation() > 0) {
              scaleFactor = scaleDownFactor;
            }
            fontScale = fontScale * scaleFactor;
            setFontScale(scaleFactor, parentFrame.getComponents());
            SwingTools.setFontScale(scaleFactor);
            parentFrame.repaint();
          }
        });

    parentFrame.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.isControlDown()) {
              float scaleFactor = 1.0f;
              if (e.getKeyCode() == KeyEvent.VK_PLUS || e.getKeyCode() == KeyEvent.VK_EQUALS) {
                scaleFactor = scaleUpFactor;
              } else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
                scaleFactor = scaleDownFactor;
              } else {
                super.keyPressed(e);
              }
              fontScale = fontScale * scaleFactor;
              setFontScale(scaleFactor, parentFrame.getComponents());
              SwingTools.setFontScale(scaleFactor);
              parentFrame.repaint();
            } else {
              super.keyPressed(e);
            }
          }
        });

    this.frame = parentFrame;
    this.frame.setTitle("JSoar Debugger - " + proxy.getName());

    this.agent = proxy;
    proxy.getInterpreter().addCommand("load-plugin", loadPluginCommand);

    this.docking = new CControl(this.frame);
    this.docking.setTheme(ThemeMap.KEY_ECLIPSE_THEME);

    // Track selection to active view
    this.docking.addFocusListener(
        new CFocusListener() {

          @Override
          public void focusLost(CDockable dockable) {}

          @Override
          public void focusGained(final CDockable newDockable) {
            SelectionProvider provider = Adaptables.adapt(newDockable, SelectionProvider.class);
            if (provider != null) {
              selectionManager.setSelectionProvider(provider);
            }

            // HACK: For some reason the WM tree briefly gets focus which messes this
            // up when using a hotkey to switch to the trace view. So invoke later
            // after everything settles down.
            SwingUtilities.invokeLater(
                new Runnable() {
                  public void run() {
                    if (newDockable instanceof AbstractAdaptableView) {
                      ((AbstractAdaptableView) newDockable).activate();
                    }
                  }
                });
          }
        });
    this.add(docking.getContentArea(), BorderLayout.CENTER);

    initActions();

    this.add(status = new StatusBar(proxy), BorderLayout.SOUTH);

    factory = new StopCommandViewFactory(this);
    factory.setDefaultLocation(docking.getDefaultLocation());

    docking.addMultipleDockableFactory("stop-command-factory", factory);
    initViews();
    initMenuBar();
    initToolbar();

    // Track the agent name in the title bar
    saveListener(
        proxy
            .getProperties()
            .addListener(
                SoarProperties.NAME,
                new PropertyListener<String>() {

                  @Override
                  public void propertyChanged(PropertyChangeEvent<String> event) {
                    frame.setTitle("JSoar Debugger - " + event.getNewValue());
                  }
                }));

    // Track agent's running state
    saveListener(
        proxy
            .getProperties()
            .addListener(
                SoarProperties.IS_RUNNING,
                new PropertyListener<Boolean>() {

                  @Override
                  public void propertyChanged(PropertyChangeEvent<Boolean> event) {
                    updateActionsAndStatus();
                  }
                }));

    // Update after init-soar
    proxy
        .getEvents()
        .addListener(
            AfterInitSoarEvent.class,
            saveListener(
                new SoarEventListener() {

                  @Override
                  public void onEvent(SoarEvent event) {
                    proxy.getPrinter().flush();
                    newUpdateCompleter(true).finish(null);
                  }
                }));

    // Update when the agent stops running
    proxy
        .getEvents()
        .addListener(
            StopEvent.class,
            saveListener(
                new SoarEventListener() {

                  @Override
                  public void onEvent(SoarEvent event) {
                    proxy.getPrinter().flush();
                    newUpdateCompleter(false).finish(null);
                  }
                }));

    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    frame.addWindowListener(
        new WindowAdapter() {

          @Override
          public void windowClosing(WindowEvent e) {
            final Preferences winPrefs = getWindowPrefs();
            final Rectangle r = frame.getBounds();
            if (frame.getExtendedState() == JFrame.NORMAL) {
              winPrefs.putInt("x", r.x);
              winPrefs.putInt("y", r.y);
              winPrefs.putInt("width", r.width);
              winPrefs.putInt("height", r.height);
            }

            exit();
          }
        });

    final Preferences winPrefs = getWindowPrefs();
    if (winPrefs.get("x", null) != null) {
      frame.setBounds(
          winPrefs.getInt("x", 0),
          winPrefs.getInt("y", 0),
          winPrefs.getInt("width", 1200),
          winPrefs.getInt("height", 1024));
    } else {
      frame.setSize(1200, 1024);
      frame.setLocationRelativeTo(null); // center
    }
    readDefaultLayout();

    update(false);
  }

  public static void setFontScale(float scaleFactor, Component[] components) {
    //        SwingTools.setFontScale(scaleFactor);

    for (Component c : components) {
      if (c instanceof Container) {
        setFontScale(scaleFactor, ((Container) c).getComponents());
      }
      c.setFont(c.getFont().deriveFont(c.getFont().getSize() * scaleFactor));
    }
  }

  private void readDefaultLayout() {
    try {
      final InputStream in = JSoarDebugger.class.getResourceAsStream("layout.xml");
      if (in != null) {
        try {
          final XElement xml = XIO.readUTF(in);
          this.docking.readXML(xml);
        } finally {
          in.close();
        }
      }
    } catch (IOException e) {
      logger.error("Failed to load default debugger layout: " + e.getMessage(), e);
    }
  }

  private Preferences getWindowPrefs() {
    return getPreferences().node("window");
  }

  private <T> void saveListener(PropertyListenerHandle<T> listener) {
    propertyListeners.add(listener);
  }

  private SoarEventListener saveListener(SoarEventListener listener) {
    soarEventListeners.add(listener);
    return listener;
  }

  private void initViews() {
    addView(new ProductionEditView(this));
    addView(new ProductionListView(this));
    addView(new PartialMatchesView(this));
    addView(new MatchSetView(this));
    addView(new TraceView(this));
    addView(new WmeSearchView(this));
    addView(new WmeSupportView(this));
    addView(new PreferencesView(this));
    addView(new GoalStackView(this));
    addView(new GdsView(this));
    addView(new WorkingMemoryView(this));
  }

  private <T extends MultipleCDockable> T addView(T view) {
    return addView(view, true);
  }

  private <T extends MultipleCDockable> T addView(T view, boolean visible) {
    views.add(view);
    docking.addDockable(view);
    if (visible) {
      view.setVisible(true);
    }
    return view;
  }

  private <T extends AbstractAdaptableView> T addView(T view) {
    return addView(view, true);
  }

  private <T extends AbstractAdaptableView> T addView(T view, boolean visible) {
    views.add(view);
    docking.addDockable(view);
    if (visible) {
      view.setVisible(true);
    }
    return view;
  }

  public ThreadedAgent getAgent() {
    return agent;
  }

  public SelectionManager getSelectionManager() {
    return selectionManager;
  }

  public RunControlModel getRunControlModel() {
    return runControlModel;
  }

  public ActionManager getActionManager() {
    return actionManager;
  }

  public void updateActionsAndStatus() {
    if (SwingUtilities.isEventDispatchThread()) {
      actionManager.updateActions();
      status.refresh(false);
    } else {
      SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              updateActionsAndStatus();
            }
          });
    }
  }

  void addPlugin(JSoarDebuggerPlugin plugin) {
    plugins.add(plugin);
  }

  public void exit() {
    detach();
    if (resetPreferencesAtExit) {
      try {
        getPreferences().removeNode();
      } catch (BackingStoreException e) {
        logger.error(e.getMessage(), e);
      }
      try {
        Prefs.getLayoutFile().deleteOnExit();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    final Object closeAction = providerProperties.get(DebuggerProvider.CLOSE_ACTION);
    if (closeAction == null || closeAction == CloseAction.EXIT) {
      System.exit(0);
    }
  }

  public static Preferences getPreferences() {
    if (PREFERENCES == null) {
      System.setProperty("java.util.prefs.PreferencesFactory", PrefsFactory.class.getName());

      PREFERENCES = Preferences.userRoot().node("org/jsoar/debugger");
    }
    return PREFERENCES;
  }

  public void restoreLayout() {
    try {
      docking.readXML(Prefs.getLayoutFile());
    } catch (IOException e) {
      e.printStackTrace();
    }
    // TODO: Implement layout storage in a way that doesn't suck.
  }

  private void initActions() {
    new ExitAction(actionManager);
    new RunAction(actionManager);
    new StopAction(actionManager);
    new InitSoarAction(actionManager);
    new SourceFileAction(actionManager);
    new ExciseProductionAction(actionManager);
    new SetBreakpointAction(actionManager);
    new AboutAction(actionManager);
    new EditProductionAction(actionManager);
    new RestoreLayoutAction(actionManager);
    new StopCommandAction(actionManager);
    new MatchesProductionAction(actionManager, false);
    new MatchesProductionAction(actionManager, true);
  }

  @SuppressWarnings("serial")
  private void initMenuBar() {
    final JMenuBar bar = new JMenuBar();

    final JMenu fileMenu = new JMenu("File");
    fileMenu.add(actionManager.getAction(SourceFileAction.class));
    fileMenu.addSeparator();
    fileMenu.add(
        new AbstractAction("Reset preferences ...") {

          private static final long serialVersionUID = -294498142478298760L;

          @Override
          public void actionPerformed(ActionEvent e) {
            resetPreferencesAtExit = true;
            JOptionPane.showMessageDialog(
                frame, "Preferences will be reset next time the debugger is loaded.");
          }
        });
    fileMenu.addSeparator();
    fileMenu.add(actionManager.getAction(ExitAction.class));

    bar.add(fileMenu);

    final RootMenuPiece viewMenu = new RootMenuPiece("View", false);

    // L&F is cute, but for some reason, switching L&F breaks the trace command box
    // viewMenu.add(new SubmenuPiece( "Look and feel", true, new CLookAndFeelMenuPiece( docking )));
    viewMenu.getMenu().addSeparator();
    viewMenu.getMenu().add(actionManager.getAction(StopCommandAction.class));

    viewMenu.add(
        new SeparatingMenuPiece(
            new SubmenuPiece("Theme", true, new CThemeMenuPiece(docking)), true, true, false));

    final SubmenuPiece layoutMenu =
        new SubmenuPiece("Layout", false, new CLayoutChoiceMenuPiece(docking, false));
    layoutMenu.getMenu().add(actionManager.getAction(RestoreLayoutAction.class));
    viewMenu.add(layoutMenu);

    viewMenu
        .getMenu()
        .add(
            new AbstractAction("Save Layout to File") {
              @Override
              public void actionPerformed(ActionEvent e) {
                try {
                  docking.writeXML(Prefs.getLayoutFile());
                } catch (IOException e1) {
                  // TODO Auto-generated catch block
                  e1.printStackTrace();
                }
              }
            });
    new ViewSelectionMenu(docking, viewMenu.getMenu());

    bar.add(viewMenu.getMenu());

    final JMenu runMenu = new JMenu("Run");
    runMenu.add(actionManager.getAction(RunAction.class));
    runMenu.add(actionManager.getAction(StopAction.class));
    runMenu.addSeparator();
    runMenu.add(new StepAction(actionManager, "Forever", RunType.FOREVER, "ctrl shift F"));
    runMenu.add(
        new StepAction(actionManager, "1 Elaboration", RunType.ELABORATIONS, "ctrl shift E"));
    runMenu.add(new StepAction(actionManager, "1 Phase", RunType.PHASES, "ctrl shift P"));
    runMenu.add(new StepAction(actionManager, "1 Decision", RunType.DECISIONS, "ctrl shift D"));
    runMenu.addSeparator();
    runMenu.add(actionManager.getAction(InitSoarAction.class));
    bar.add(runMenu);

    final JMenu toolsMenu = new JMenu("Tools");
    toolsMenu.add(new GarbageCollectorAction(actionManager));
    bar.add(toolsMenu);

    final JMenu helpMenu = new JMenu("Help");
    helpMenu.add(
        new UrlAction(actionManager, "JSoar Home Page", resources.getString("jsoar.site.url")));
    helpMenu.add(
        new UrlAction(actionManager, "CSoar Home Page", resources.getString("csoar.site.url")));
    helpMenu.add(
        new AbstractAction("Command Help") {

          @Override
          public void actionPerformed(ActionEvent e) {
            Callable<Void> callable =
                () -> {
                  SoarCommand cmd = getAgent().getAgent().getInterpreter().getCommand("help", null);
                  cmd.execute(null, new String[] {"help"});
                  getAgent().getAgent().getPrinter().flush();
                  return null;
                };
            getAgent().execute(callable, null);
          }
        });
    helpMenu.add(
        new UrlAction(
            actionManager,
            "Quick Help",
            JSoarDebugger.class
                .getResource("/org/jsoar/debugger/jsoar quick help.html")
                .toString()));
    helpMenu.addSeparator();
    helpMenu.add(actionManager.getAction(AboutAction.class));
    bar.add(helpMenu);

    frame.setJMenuBar(bar);
  }

  private void initToolbar() {
    JToolBar bar = new JToolBar();
    bar.setFloatable(false);

    bar.add(new RunControlPanel(this));
    add(bar, BorderLayout.NORTH);
  }

  public void update(final boolean afterInitSoar) {
    updateActionsAndStatus();

    final List<Refreshable> refreshables = Adaptables.adaptCollection(views, Refreshable.class);
    for (Refreshable r : refreshables) {
      r.refresh(afterInitSoar);
    }
  }

  /**
   * Executes the CompletionHandler on the Swing thread. Do not do any agent interaction in here!
   *
   * @param afterInitSoar True if calling this in response to Soar being (re)initialized
   */
  public CompletionHandler<Void> newUpdateCompleter(final boolean afterInitSoar) {
    return SwingCompletionHandler.newInstance(
        new CompletionHandler<Void>() {

          @Override
          public void finish(Void result) {
            update(afterInitSoar);
          }
        });
  }

  public static void main(final String[] args) {
    SwingTools.initializeLookAndFeel();
    float scale = Float.parseFloat(System.getProperty("fontScale", "1.0"));
    SwingTools.setFontScale(scale);
    fontScale = scale;
    SwingUtilities.invokeLater(
        new Runnable() {

          public void run() {
            initialize(args);
          }
        });
  }

  /**
   * Attach a new debugger window to the given threaded agent. This function <b>must</b> be called
   * from the Swing event thread!
   *
   * @param proxy an <b>initialized</b> threaded agent to attach to
   * @return the debugger
   */
  public static JSoarDebugger attach(ThreadedAgent proxy, Map<String, Object> properties) {
    synchronized (debuggers) {
      JSoarDebugger debugger = debuggers.get(proxy);
      if (debugger == null) {
        // DockingManager.setFloatingEnabled(true);

        debugger = new JSoarDebugger(properties);

        final JXFrame frame = new JXFrame();

        frame.setContentPane(debugger);

        debugger.initialize(frame, proxy);

        frame.setVisible(true);

        debuggers.put(proxy, debugger);
      } else {
        debugger.frame.setVisible(true);
        debugger.frame.requestFocus();
      }
      return debugger;
    }
  }

  /** Detach debugger from agent */
  public void detach() {
    synchronized (debuggers) {
      logger.info(String.format("Detaching from agent '" + agent + "'"));

      // clean up soar prop listeners
      for (PropertyListenerHandle<?> listener : propertyListeners) {
        listener.removeListener();
      }

      // clean up soar event listener
      for (SoarEventListener listener : soarEventListeners) {
        agent.getEvents().removeListener(null, listener);
      }
      soarEventListeners.clear();

      // clean up disposables
      runControlModel.dispose();
      for (Disposable d : Adaptables.adaptCollection(views, Disposable.class)) {
        d.dispose();
      }
      views.clear();

      if (frame.isVisible()) {
        frame.setVisible(false);
      }

      for (JSoarDebuggerPlugin plugin : plugins) {
        plugin.shutdown();
      }
      plugins.clear();

      debuggers.remove(agent);

      // If the agent was created by this debugger, dispose it. This is important
      // so things like SMEM database will be flushed at shutdown.
      if (shouldDisposeAgent()) {
        agent.dispose();
      }
    }
  }

  private boolean shouldDisposeAgent() {
    return this == agent.getProperties().get(CREATED_BY)
        || EnumSet.of(CloseAction.EXIT, CloseAction.DISPOSE)
            .contains((CloseAction) providerProperties.get(DebuggerProvider.CLOSE_ACTION));
  }

  /**
   * This is identical to {@link #main(String[])}, but it must be called from the Swing event
   * thread. Also the look and feel must be initialized prior to calling this.
   *
   * @param args command-line arguments
   */
  public static JSoarDebugger initialize(final String[] args) {
    final ThreadedAgent agent = ThreadedAgent.create();
    final JSoarDebugger debugger = attach(agent, new HashMap<String, Object>());
    agent
        .getProperties()
        .setProvider(
            CREATED_BY,
            new PropertyProvider<JSoarDebugger>() {
              @Override
              public JSoarDebugger get() {
                return debugger;
              }

              @Override
              public JSoarDebugger set(JSoarDebugger value) {
                throw new UnsupportedOperationException("Can't set " + CREATED_BY);
              }
            });

    debugger.agent.execute(
        () -> {
          for (String arg : args) {
            try {
              SoarCommands.source(debugger.getAgent().getInterpreter(), arg);
            } catch (SoarException e) {
              logger.error("Error sourcing file '" + arg + "': " + e.getMessage(), e);
              debugger
                  .getAgent()
                  .getPrinter()
                  .error("Error sourcing file '%s': %s", arg, e.getMessage());
            }
          }
          debugger.getAgent().getPrinter().flush();
          return null;
        },
        debugger.newUpdateCompleter(false));
    return debugger;
  }

  /**
   * Creates and returns a {@link DebuggerProvider} that will open this debugger when an agent calls
   * the debug RHS function
   *
   * @return a new debugger provider that opens this debugger with a default configuration
   */
  public static DebuggerProvider newDebuggerProvider() {
    return newDebuggerProvider();
  }

  /* (non-Javadoc)
   * @see org.jsoar.util.adaptables.Adaptable#getAdapter(java.lang.Class)
   */
  @Override
  public Object getAdapter(Class<?> klass) {
    if (klass.equals(SoarCommandInterpreter.class)) {
      return getAgent().getInterpreter();
    }
    if (klass.equals(Agent.class)) {
      return agent.getAgent();
    }
    if (klass.equals(ThreadedAgent.class)) {
      return agent;
    }
    if (klass.equals(SelectionManager.class)) {
      return selectionManager;
    }
    if (klass.equals(RunControlModel.class)) {
      return runControlModel;
    }
    if (klass.equals(ActionManager.class)) {
      return actionManager;
    }
    Object o = Adaptables.findAdapter(views, klass);
    if (o != null) {
      return o;
    }
    return Adaptables.findAdapter(plugins, klass);
  }

  @Override
  public String toString() {
    return frame.getTitle();
  }

  public void addStopCommandView() {
    factory.setDefaultLocation(views.get(0).getBaseLocation());
    addView(new StopCommandView(factory, this));
  }
}
