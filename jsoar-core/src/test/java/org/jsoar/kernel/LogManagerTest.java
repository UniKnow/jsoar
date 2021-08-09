package org.jsoar.kernel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jsoar.kernel.LogManager.EchoMode;
import org.jsoar.kernel.LogManager.LogLevel;
import org.jsoar.kernel.LogManager.LoggerException;
import org.jsoar.kernel.LogManager.SourceLocationMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class LogManagerTest {

  private Agent agent;

  @Before
  public void setUp() {
    agent = new Agent();
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test
  public void testLogManagerCreation() {
    LogManager logManager = agent.getLogManager();
    assertNotNull(logManager);
  }

  @Test
  public void testLogManagerInit() throws Exception {
    LogManager logManager = agent.getLogManager();

    Set<String> testSet = new HashSet<>();
    testSet.add("default");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger");
    testSet.add("test-logger");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.init();
    testSet.clear();
    testSet.add("default");
    assertEquals(testSet, logManager.getLoggerNames());
  }

  @Test(expected = LoggerException.class)
  public void testAddLoggerThrowsExceptionInStrictModeIfAddingExistingLogger()
      throws LoggerException {
    // Given a log manager
    LogManager logManager = agent.getLogManager();
    // And log manager is strict
    logManager.setStrict(true);
    // And existing logger "existing-logger";
    logManager.addLogger("existing-logger");

    // When adding logger "existing-logger"
    // Then LoggerException should occur
    logManager.addLogger("existing-logger");
  }

  @Test
  public void testLogAdd() throws Exception {
    LogManager logManager = agent.getLogManager();

    logManager.setStrict(false);
    assertFalse(logManager.isStrict());

    Set<String> testSet = new HashSet<>();
    testSet.add("default");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger");
    testSet.add("test-logger");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger2");
    testSet.add("test-logger2");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger3");
    testSet.add("test-logger3");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.init();
    testSet.clear();
    testSet.add("default");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger4");
    testSet.add("test-logger4");
    assertEquals(testSet, logManager.getLoggerNames());
  }

  @Test
  public void testLogAddStrict() throws Exception {
    LogManager logManager = agent.getLogManager();

    logManager.setStrict(true);
    assertTrue(logManager.isStrict());

    Set<String> testSet = new HashSet<>();
    testSet.add("default");
    assertEquals(testSet, logManager.getLoggerNames());

    logManager.addLogger("test-logger");
    testSet.add("test-logger");
    assertEquals(testSet, logManager.getLoggerNames());

    boolean success = false;
    try {
      logManager.log(
          "test-logger2", LogLevel.error, Collections.singletonList("test-string"), false);
    } catch (LoggerException e) {
      success = true;
    } finally {
      assertTrue(success);
    }

    logManager.addLogger("test-logger2");
    testSet.add("test-logger2");
    assertEquals(testSet, logManager.getLoggerNames());

    success = true;
    try {
      logManager.log(
          "test-logger2", LogLevel.error, Collections.singletonList("test-string"), false);
    } catch (LoggerException e) {
      success = false;
    } finally {
      assertTrue(success);
    }

    success = false;
    try {
      logManager.addLogger("test-logger");
    } catch (LoggerException e) {
      success = true;
    } finally {
      assertTrue(success);
    }
  }

  @Test
  public void testLogEnableDisable() {
    LogManager logManager = agent.getLogManager();

    logManager.setActive(true);
    assertTrue(logManager.isActive());

    logManager.setActive(false);
    assertFalse(logManager.isActive());

    logManager.setActive(true);
    assertTrue(logManager.isActive());
  }

  @Test
  public void testLogStrictEnableDisable() {
    LogManager logManager = agent.getLogManager();

    logManager.setStrict(true);
    assertTrue(logManager.isStrict());

    logManager.setStrict(false);
    assertFalse(logManager.isStrict());

    logManager.setStrict(true);
    assertTrue(logManager.isStrict());
  }

  @Test
  public void testGetLogLevelFromString() {
    for (LogLevel level : LogLevel.values()) {
      assertEquals(level, LogLevel.fromString(level.toString().toLowerCase()));
      assertEquals(level, LogLevel.fromString(level.toString().toUpperCase()));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetLogLevelFromStringThrowsExceptionIfValueIsNull() {
    LogLevel.fromString(null);
  }

  @Test
  public void testGetSourceLocationMethodFromString() {
    for (SourceLocationMethod locationMethod : SourceLocationMethod.values()) {
      assertEquals(locationMethod,
          SourceLocationMethod.fromString(locationMethod.toString().toLowerCase()));
      assertEquals(locationMethod,
          SourceLocationMethod.fromString(locationMethod.toString().toUpperCase()));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetSourceLocationMethodFromStringThrowsExceptionIfValueIsNull() {
    SourceLocationMethod.fromString(null);
  }

  @Test
  public void testGetEchoModeFromString() {
    for (EchoMode echoMode : EchoMode.values()) {
      assertEquals(echoMode,
          EchoMode.fromString(echoMode.toString().toLowerCase()));
      assertEquals(echoMode,
          EchoMode.fromString(echoMode.toString().toUpperCase()));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetEchoModeFromStringThrowsExceptionIfValueIsNull() {
    EchoMode.fromString(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetLoggerThrowsExceptionIfNameNull() throws LoggerException {
    LogManager logManager = agent.getLogManager();
    logManager.getLogger(null);
  }

  @Test(expected = LoggerException.class)
  public void testGetLoggerThrowsExceptionIfNonExistingLoggerAndStrictMode()
      throws LoggerException {
    // Given a log manager in strict mode
    LogManager logManager = agent.getLogManager();
    logManager.setStrict(true);

    // When retrieving non existing logger
    // Then LogException is thrown
    logManager.getLogger("NON-EXISTING");
  }

  @Test
  public void testGetLoggerIfNonExisting()
      throws LoggerException {
    // Given a log manager
    LogManager logManager = agent.getLogManager();

    // When retrieving non existing logger
    int numberOfLoggers = logManager.getLoggerCount();
    Logger logger = logManager.getLogger("NON-EXISTING");

    // Then new logger is returned
    assertNotNull(logger);
    assertEquals(numberOfLoggers+1, logManager.getLoggerCount());
  }

}
