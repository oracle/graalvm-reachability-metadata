/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.javabean.JavaBeanConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanConverterTest {
    @Test
    void marshalsNullJavaBeanPropertiesWithNullTypeMarker() {
        XStream xstream = configuredXStream();
        Document document = new Document();
        document.setTitle("XStream guide");
        document.setDescription(null);

        String xml = xstream.toXML(document);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("<title>XStream guide</title>");
        assertThat(xml).contains("<description class=\"null\"/>");
        assertThat(restored).isInstanceOf(Document.class);
        Document restoredDocument = (Document)restored;
        assertThat(restoredDocument.getTitle()).isEqualTo("XStream guide");
        assertThat(restoredDocument.getDescription()).isNull();
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{Document.class});
        xstream.alias("document", Document.class);
        xstream.registerConverter(new JavaBeanConverter(xstream.getMapper(), Document.class), XStream.PRIORITY_VERY_HIGH);
        return xstream;
    }

    public static final class Document {
        private String title;
        private String description;

        public Document() {
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
