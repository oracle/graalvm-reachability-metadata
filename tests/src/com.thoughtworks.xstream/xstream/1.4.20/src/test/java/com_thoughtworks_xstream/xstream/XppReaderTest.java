/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringReader;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.XppReader;

import io.github.xstream.mxparser.MXParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class XppReaderTest {
    @Test
    void readsElementsTextAndAttributesWithProvidedParser() {
        HierarchicalStreamReader reader = new XppReader(new StringReader(
            "<order status=\"created\"><id>42</id><item>book</item></order>"), new MXParser());

        try {
            assertThat(reader.getNodeName()).isEqualTo("order");
            assertThat(reader.getAttribute("status")).isEqualTo("created");
            assertThat(reader.getAttributeCount()).isEqualTo(1);
            assertThat(reader.getAttributeName(0)).isEqualTo("status");
            assertThat(reader.getAttribute(0)).isEqualTo("created");
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

    @Test
    void deprecatedConstructorAttemptsToCreateDefaultParser() {
        HierarchicalStreamReader reader = null;
        try {
            reader = new XppReader(new StringReader("<order status=\"created\"><id>42</id></order>"));
            assertThat(reader.getNodeName()).isEqualTo("order");
            assertThat(reader.getAttribute("status")).isEqualTo("created");

            reader.moveDown();
            assertThat(reader.getNodeName()).isEqualTo("id");
            assertThat(reader.getValue()).isEqualTo("42");
            reader.moveUp();
        } catch (StreamException e) {
            assertThat(e).hasMessageContaining("Cannot create Xpp3 parser instance");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
