/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.jboss.resteasy.annotations.providers.jaxb.JAXBConfig;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBContextWrapper;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class JAXBContextWrapperTest {
    @Test
    void loadsTheConfiguredClasspathSchemaAndValidatesXml() throws Exception {
        JAXBContextWrapper context = new JAXBContextWrapper(new InventoryConfig(), Inventory.class);
        Inventory inventory = new Inventory();
        inventory.quantity = 7;
        StringWriter output = new StringWriter();
        context.createMarshaller().marshal(inventory, output);

        assertThat(context.getSchema()).isNotNull();
        Validator validator = context.getSchema().newValidator();
        validator.validate(new StreamSource(new StringReader(output.toString())));
        Inventory restored = (Inventory) context.createUnmarshaller()
                .unmarshal(new StringReader(output.toString()));
        assertThat(restored.quantity).isEqualTo(7);

        assertThatThrownBy(() -> validator.validate(new StreamSource(new StringReader("""
                <inventory><quantity>many</quantity></inventory>
                """)))).isInstanceOf(SAXException.class);
    }

    @XmlRootElement(name = "inventory")
    public static class Inventory {
        public int quantity;
    }

    private static final class InventoryConfig implements JAXBConfig {
        @Override
        public Class<? extends Annotation> annotationType() {
            return JAXBConfig.class;
        }

        @Override
        public boolean useNameSpacePrefix() {
            return false;
        }

        @Override
        public XmlNs[] namespaces() {
            return new XmlNs[0];
        }

        @Override
        public boolean validate() {
            return true;
        }

        @Override
        public String schema() {
            return "inventory.xsd";
        }

        @Override
        public String schemaType() {
            return XMLConstants.W3C_XML_SCHEMA_NS_URI;
        }
    }
}
