/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringReader;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DomDriverTest {
    @Test
    void createsReaderWithSecuredDocumentBuilderFactory() {
        DomDriver driver = new DomDriver();
        HierarchicalStreamReader reader = driver.createReader(new StringReader(
            "<order status=\"created\"><id>42</id><item>book</item></order>"));

        try {
            assertThat(reader.getNodeName()).isEqualTo("order");
            assertThat(reader.getAttribute("status")).isEqualTo("created");
            assertThat(reader.hasMoreChildren()).isTrue();

            reader.moveDown();
            assertThat(reader.getNodeName()).isEqualTo("id");
            assertThat(reader.getValue()).isEqualTo("42");
            reader.moveUp();

            reader.moveDown();
            assertThat(reader.getNodeName()).isEqualTo("item");
            assertThat(reader.getValue()).isEqualTo("book");
            reader.moveUp();

            assertThat(reader.hasMoreChildren()).isFalse();
        } finally {
            reader.close();
        }
    }
}
