/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.StandardStaxDriver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardStaxDriverTest {
    @Test
    void createsReaderAndWriterWithStandardStaxFactories() {
        StandardStaxDriver driver = new StandardStaxDriver();

        XMLInputFactory inputFactory = driver.getInputFactory();
        XMLOutputFactory outputFactory = driver.getOutputFactory();

        assertThat(inputFactory).isNotNull();
        assertThat(outputFactory).isNotNull();
        assertThat(inputFactory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)).isEqualTo(Boolean.FALSE);

        StringWriter xml = new StringWriter();
        HierarchicalStreamWriter writer = driver.createWriter(xml);
        writer.startNode("invoice");
        writer.addAttribute("state", "paid");
        writer.startNode("total");
        writer.setValue("125");
        writer.endNode();
        writer.endNode();
        writer.close();

        HierarchicalStreamReader reader = driver.createReader(new StringReader(xml.toString()));
        try {
            assertThat(reader.getNodeName()).isEqualTo("invoice");
            assertThat(reader.getAttribute("state")).isEqualTo("paid");
            assertThat(reader.hasMoreChildren()).isTrue();

            reader.moveDown();
            assertThat(reader.getNodeName()).isEqualTo("total");
            assertThat(reader.getValue()).isEqualTo("125");
            reader.moveUp();

            assertThat(reader.hasMoreChildren()).isFalse();
        } finally {
            reader.close();
        }
    }
}
