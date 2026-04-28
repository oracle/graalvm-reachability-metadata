/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringReader;
import java.io.StringWriter;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.SjsxpDriver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class SjsxpDriverTest {
    @Test
    void createsReaderAndWriterWithSjsxpFactories() {
        SjsxpDriver driver = new SjsxpDriver();
        StringWriter xml = new StringWriter();

        HierarchicalStreamWriter writer = driver.createWriter(xml);
        writer.startNode("order");
        writer.addAttribute("status", "created");
        writer.startNode("id");
        writer.setValue("42");
        writer.endNode();
        writer.endNode();
        writer.close();

        HierarchicalStreamReader reader = driver.createReader(new StringReader(xml.toString()));
        try {
            assertThat(reader.getNodeName()).isEqualTo("order");
            assertThat(reader.getAttribute("status")).isEqualTo("created");
            assertThat(reader.hasMoreChildren()).isTrue();

            reader.moveDown();
            assertThat(reader.getNodeName()).isEqualTo("id");
            assertThat(reader.getValue()).isEqualTo("42");
            reader.moveUp();

            assertThat(reader.hasMoreChildren()).isFalse();
        } finally {
            reader.close();
        }
    }
}
