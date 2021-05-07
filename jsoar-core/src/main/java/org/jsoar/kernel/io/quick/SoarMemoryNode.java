package org.jsoar.kernel.io.quick;

import org.jsoar.kernel.io.InputOutput;
import org.jsoar.kernel.io.InputWme;
import org.jsoar.kernel.symbols.Identifier;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.Symbols;

class SoarMemoryNode {
  private InputWme wme;
  private Identifier idValue;

  private String name;

  private SoarMemoryNode parentNode;

  private MemoryNode memoryNode;

  private Identifier getIdValue() {
    return idValue;
  }

  public InputWme getWME() {
    return wme;
  }

  public Symbol getValue() {
    return wme != null ? wme.getValue() : getIdValue();
  }

  private void setWME(InputWme wme) {
    this.wme = wme;
    this.idValue = wme != null ? wme.getValue().asIdentifier() : null;
  }

  public SoarMemoryNode(Identifier idValue) {
    this.idValue = idValue;
    this.memoryNode = new MemoryNode();
  }

  public SoarMemoryNode(String name) {
    this.name = name;
    this.memoryNode = new MemoryNode();
  }

  public void setParentNode(SoarMemoryNode parentNode) {
    this.parentNode = parentNode;
  }

  private void createWME(InputOutput io, MemoryNode node) {
    assert (wme == null && parentNode != null);

    if (name == null) {
      return;
    }

    final Identifier parentWME = (Identifier) parentNode.getIdValue();
    final Symbol attr = Symbols.create(io.getSymbols(), name);
    final Symbol value =
        node.getValue() != null
            ? Symbols.create(io.getSymbols(), node.getValue())
            : io.getSymbols().createIdentifier(name.charAt(0));
    setWME(io.addInputWme(parentWME, attr, value));

    if (node.isString()) {
      String strVal = node.getStringValue();
      memoryNode.setStringValue(strVal);
    } else if (node.isInt()) {
      long intVal = node.getIntValue();
      memoryNode.setIntValue(intVal);
    } else if (node.isDouble()) {
      double doubleVal = node.getDoubleValue();
      memoryNode.setDoubleValue(doubleVal);
    } else {
      memoryNode.clearValue();
    }
  }

  private void updateWME(InputOutput io, MemoryNode node) {
    assert (wme != null && memoryNode.hasSameType(node));

    if (name == null) {
      return;
    }

    if (!memoryNode.valueIsEqual(node)) {
      wme.update(
          Symbols.create(
              io.getSymbols(), node.getValue() != null ? node.getValue() : Symbols.NEW_ID));
      setWME(wme);
      memoryNode.setValue(node);
    }
  }

  public void remove(InputOutput io) {
    if (wme != null) {
      wme.remove();
      setWME(null);
    }
  }

  public void synchronizeToMemoryNode(InputOutput io, MemoryNode node) {
    if (wme == null) {
      createWME(io, node);
    } else if (!memoryNode.hasSameType(node)) {
      wme.remove();
      wme = null;
      createWME(io, node);
    } else {
      updateWME(io, node);
    }
  }
}
