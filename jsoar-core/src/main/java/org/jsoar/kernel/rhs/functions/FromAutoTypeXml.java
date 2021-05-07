/*
 * Copyright (c) 2012 Soar Technology Inc.
 *
 * Created on July 10, 2012
 */
package org.jsoar.kernel.rhs.functions;

import java.io.IOException;
import java.util.List;
import org.jsoar.kernel.io.xml.AutoTypeXmlToWme;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.util.XmlTools;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * A RHS function that parses an XML string using {@link AutoTypeXmlToWme} and returns a working
 * memory representation of the XML. The root element of the XML input is ignored.
 *
 * @author chris.kawatsu
 */
public class FromAutoTypeXml extends AbstractRhsFunctionHandler {
  /** */
  public FromAutoTypeXml() {
    super("from-at-xml", 1, 1);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.jsoar.kernel.rhs.functions.RhsFunctionHandler#execute(org.jsoar.kernel
   * .rhs.functions.RhsFunctionContext, java.util.List)
   */
  @Override
  public Symbol execute(final RhsFunctionContext context, List<Symbol> arguments)
      throws RhsFunctionException {
    RhsFunctions.checkArgumentCount(this, arguments);

    final String xml = arguments.get(0).toString();
    if (xml == null) {
      throw new RhsFunctionException(
          "Only argument to '" + getName() + "' RHS function must be an XML string.");
    }

    final Document doc;
    try {
      doc = XmlTools.parse(xml);
    } catch (SAXException e) {
      throw new RhsFunctionException(e.getMessage(), e);
    } catch (IOException e) {
      throw new RhsFunctionException(e.getMessage(), e);
    }

    return AutoTypeXmlToWme.forRhsFunction(context).fromXml(doc.getDocumentElement());
  }
}
