/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.STAXEventReader;
import org.dom4j.io.STAXEventWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises dom4j's StAX event bridge subsystem to transform a shipment manifest in an application pipeline.
public class StaxEventManifestTransformerTest {
    @Test
    @Timeout(60)
    public void transformsManifestThroughStaxEvents() throws Exception {
        String manifestXml = """
                <manifest warehouse="lyon">
                  <package tracking="PKG-17">
                    <weight unit="kg">2.4</weight>
                  </package>
                </manifest>
                """;

        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        XMLEventReader eventReader = inputFactory.createXMLEventReader(new StringReader(manifestXml));
        Document manifest;
        try {
            manifest = new STAXEventReader().readDocument(eventReader);
        } finally {
            eventReader.close();
        }

        Element packageElement = manifest.getRootElement().element("package");
        packageElement.addElement("priority").addText("express");

        StringWriter output = new StringWriter();
        XMLEventWriter eventWriter = XMLOutputFactory.newFactory().createXMLEventWriter(output);
        try {
            new STAXEventWriter(eventWriter).writeDocument(manifest);
        } finally {
            eventWriter.close();
        }

        String transformed = output.toString();
        assertTrue(transformed.contains("warehouse=\"lyon\""));
        assertTrue(transformed.contains("tracking=\"PKG-17\""));
        assertTrue(transformed.contains("<priority>express</priority>"));
        assertEquals(1, countOccurrences(transformed, "<package"));
    }

    private static int countOccurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }
}
