/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.IDResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.SAXException;

/// Exercises JAXB runtime's ID/IDREF resolution subsystem for a catalog workflow.
public class IdResolverCatalogTest {

    @Test
    @Timeout(60)
    void resolvesProductReferencesToTheCatalogObjects() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Catalog.class);
        Catalog catalog = new Catalog(
                List.of(new Product("sku-17", "Desk lamp")),
                List.of(new Line("sku-17", 2)));
        Marshaller marshaller = context.createMarshaller();
        StringWriter output = new StringWriter();
        marshaller.marshal(catalog, output);

        String xml = output.toString();
        assertTrue(xml.contains("sku-17"));
        assertTrue(xml.contains("<product>sku-17</product>"));

        RecordingIdResolver resolver = new RecordingIdResolver();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setProperty(IDResolver.class.getName(), resolver);
        Catalog restored = (Catalog) unmarshaller.unmarshal(new StringReader(xml));

        assertEquals("Desk lamp", restored.products.get(0).name);
        assertEquals(2, restored.lines.get(0).quantity);
        assertSame(restored.products.get(0), restored.lines.get(0).product);
        assertEquals(List.of("sku-17"), resolver.boundIds);
        assertEquals(List.of("sku-17"), resolver.resolvedIds);
    }

    public static final class RecordingIdResolver extends IDResolver {

        private final Map<String, Object> values = new HashMap<>();
        private final List<String> boundIds = new java.util.ArrayList<>();
        private final List<String> resolvedIds = new java.util.ArrayList<>();

        @Override
        public void bind(String id, Object value) throws SAXException {
            values.put(id, value);
            boundIds.add(id);
        }

        @Override
        public Callable<?> resolve(String id, Class type) throws SAXException {
            resolvedIds.add(id);
            return () -> values.get(id);
        }
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Catalog {

        @XmlElement(name = "product")
        private List<Product> products;

        @XmlElement(name = "line")
        private List<Line> lines;

        public Catalog() {
        }

        private Catalog(List<Product> products, List<Line> lines) {
            this.products = products;
            this.lines = lines;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Product {

        @XmlAttribute
        @XmlID
        private String sku;

        private String name;

        public Product() {
        }

        private Product(String sku, String name) {
            this.sku = sku;
            this.name = name;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Line {

        @XmlElement(name = "product")
        @XmlIDREF
        private Product product;

        private int quantity;

        public Line() {
        }

        private Line(String sku, int quantity) {
            this.product = new Product(sku, null);
            this.quantity = quantity;
        }
    }
}
