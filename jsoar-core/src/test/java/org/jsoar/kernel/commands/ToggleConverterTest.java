package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;

import org.jsoar.kernel.commands.ToggleConverter.Toggle;
import org.junit.Test;

public class ToggleConverterTest {

  @Test
  public void testConvertValueEnabled() {
    convertValue("--enable", true);
    convertValue("--ENABLE", true);
  }

  @Test
  public void testConvertValueDisable() {
    convertValue("--disable", false);
    convertValue("--DISABLE", false);
  }

  @Test
  public void testConvertValueOn() {
    convertValue("--on", true);
    convertValue("--ON", true);
  }

  @Test
  public void testConvertValueOff() {
    convertValue("--off", false);
    convertValue("--OFF", false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConvertThrowsExceptionIfValueIsNull() {
    ToggleConverter converter = new ToggleConverter();
    converter.convert(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConvertThrowsExceptionIfValueInvalid() {
    ToggleConverter converter = new ToggleConverter();
    converter.convert("invalid");
  }

  private void convertValue(String value, boolean expectedResult) {
    ToggleConverter converter = new ToggleConverter();
    Toggle result = converter.convert(value);
    assertEquals(expectedResult, result.asBoolean());
  }
}
