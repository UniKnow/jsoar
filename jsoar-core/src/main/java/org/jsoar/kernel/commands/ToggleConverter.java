package org.jsoar.kernel.commands;

import lombok.NonNull;
import org.jsoar.kernel.commands.ToggleConverter.Toggle;
import picocli.CommandLine.ITypeConverter;

public class ToggleConverter implements ITypeConverter<Toggle> {

  @Override
  public Toggle convert(@NonNull String value) {
    switch (value.toLowerCase()) {
      case "-e", "--on", "--enable":
        return new Toggle(true);
      case "-d", "--off", "--disable":
        return new Toggle(false);
      default:
        throw new IllegalArgumentException("Expected one argument: on | off");
    }
  }

  public static class Toggle {
    private final boolean value;

    private Toggle(final boolean value) {
      this.value = value;
    }

    public boolean asBoolean() {
      return value;
    }

    public String toString() {
      return value ? "on" : "off";
    }
  }
}
