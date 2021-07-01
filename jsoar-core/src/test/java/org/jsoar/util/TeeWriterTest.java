/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 20, 2008
 */
package org.jsoar.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.Writer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** @author ray */
public class TeeWriterTest {

  /** @throws java.lang.Exception */
  @Before
  public void setUp() throws Exception {}

  /** @throws java.lang.Exception */
  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFlush() throws IOException {
    // Given a TeeWriter containing multiple writers
    Writer firstWriter = mock(Writer.class);
    Writer secondWriter = mock(Writer.class);
    try (TeeWriter teeWriter = new TeeWriter(firstWriter, secondWriter)) {

      // When flushing TeeWriter
      teeWriter.flush();

      // Then all contained writers are flushed also
      verify(firstWriter, times(1)).flush();
      verify(secondWriter, times(1)).flush();
    }
  }

  @Test
  public void testClose() throws IOException {
    // Given a TeeWriter containing multiple writers
    Writer firstWriter = mock(Writer.class);
    Writer secondWriter = mock(Writer.class);
    TeeWriter teeWriter = new TeeWriter(firstWriter, secondWriter);

    // When closing TeeWriter
    teeWriter.close();

    // Then all contained writers are closed also
    verify(firstWriter, times(1)).close();
    verify(secondWriter, times(1)).close();
  }

  @Test
  public void testWrite() throws Exception {
    // Given a TeeWriter containing multiple writers
    Writer firstWriter = mock(Writer.class);
    Writer secondWriter = mock(Writer.class);
    try (TeeWriter teeWriter = new TeeWriter(firstWriter, secondWriter)) {

      // When writing text to TeeWriter
      String text = "Test TeeWriter";
      teeWriter.write(text, 0, text.length());

      // Then text is written to all contained TeeWriters
      char[] writtenText = new char[1024];
      text.getChars(0, text.length(), writtenText, 0);
      verify(firstWriter, times(1)).write(writtenText, 0, text.length());
      verify(secondWriter, times((1))).write(writtenText, 0, text.length());
    }
  }

  @Test
  public void testAppend() throws Exception {
    // Given a TeeWriter containing multiple writers
    Writer firstWriter = mock(Writer.class);
    Writer secondWriter = mock(Writer.class);
    try (TeeWriter teeWriter = new TeeWriter(firstWriter, secondWriter)) {

      // When appending text to TeeWriter
      String text = "Test TeeWriter";
      teeWriter.append(text);

      // Then text is written to all contained TeeWriters
      char[] writtenText = new char[1024];
      text.getChars(0, text.length(), writtenText, 0);
      verify(firstWriter, times(1)).write(writtenText, 0, text.length());
      verify(secondWriter, times((1))).write(writtenText, 0, text.length());
    }
  }

  @Test
  public void testAddWriter() throws IOException {
    // Given a existing TeeWriter containing single writers
    Writer firstWriter = mock(Writer.class);
    try (TeeWriter teeWriter = new TeeWriter(firstWriter)) {

      // When adding second writer
      Writer secondWriter = mock(Writer.class);
      teeWriter.addWriter(secondWriter);
      // And flushing TeeWriter
      teeWriter.flush();

      // Then all contained writers are flushed also
      verify(firstWriter, times(1)).flush();
      verify(secondWriter, times(1)).flush();
    }
  }

  @Test
  public void testRemoveWriter() throws IOException {
    // Given a existing TeeWriter containing multiple writers
    Writer firstWriter = mock(Writer.class);
    Writer secondWriter = mock(Writer.class);
    try (TeeWriter teeWriter = new TeeWriter(firstWriter, secondWriter)) {

      // When removing writer
      teeWriter.removeWriter(secondWriter);
      // And flushing TeeWriter
      teeWriter.flush();

      // Then only remaining writers are flushed also
      verify(firstWriter, times(1)).flush();
      verify(secondWriter, never()).flush();
    }
  }
}
