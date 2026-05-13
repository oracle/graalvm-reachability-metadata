/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.apache.xerces.util.AugmentationsImpl;
import org.apache.xerces.util.XMLAttributesImpl;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.cyberneko.html.filters.DefaultFilter;
import org.cyberneko.html.filters.NamespaceBinder;
import org.junit.jupiter.api.Test;

public class NamespaceBinderTest {
    private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";
    private static final String OVERRIDE_NAMESPACES = "http://cyberneko.org/html/features/override-namespaces";
    private static final String INSERT_NAMESPACES = "http://cyberneko.org/html/features/insert-namespaces";
    private static final String NAMES_ELEMS = "http://cyberneko.org/html/properties/names/elems";
    private static final String NAMES_ATTRS = "http://cyberneko.org/html/properties/names/attrs";
    private static final String NAMESPACES_URI = "http://cyberneko.org/html/properties/namespaces-uri";

    @Test
    void startAndEndElementPublishDeclaredNamespaceMappings() throws Exception {
        NamespaceBinder binder = newConfiguredBinder();
        RecordingDocumentHandler handler = new RecordingDocumentHandler();
        XMLAttributesImpl attributes = new XMLAttributesImpl();
        attributes.addAttribute(new QName(null, "xmlns:svg", "xmlns:svg", null), "CDATA", "urn:svg");
        QName element = new QName(null, null, "svg:rect", null);
        Augmentations augmentations = new AugmentationsImpl();

        binder.setDocumentHandler(handler);
        binder.startElement(element, attributes, augmentations);
        binder.endElement(element, augmentations);

        assertEquals(List.of("svg=urn:svg"), handler.startedPrefixMappings);
        assertEquals(List.of("svg"), handler.endedPrefixMappings);
        assertEquals("urn:svg", element.uri);
        assertEquals(1, handler.startElementCount);
        assertEquals(1, handler.endElementCount);
        assertSame(augmentations, handler.startElementAugmentations);
        assertSame(augmentations, handler.endElementAugmentations);
    }

    @Test
    void emptyElementPublishesAndClosesNamespaceMappingsAroundEvent() throws Exception {
        NamespaceBinder binder = newConfiguredBinder();
        RecordingDocumentHandler handler = new RecordingDocumentHandler();
        XMLAttributesImpl attributes = new XMLAttributesImpl();
        attributes.addAttribute(new QName(null, "xmlns:math", "xmlns:math", null), "CDATA", "urn:math");
        QName element = new QName(null, null, "math:mi", null);
        Augmentations augmentations = new AugmentationsImpl();

        binder.setDocumentHandler(handler);
        binder.emptyElement(element, attributes, augmentations);

        assertEquals(List.of("math=urn:math"), handler.startedPrefixMappings);
        assertEquals(List.of("math"), handler.endedPrefixMappings);
        assertEquals("urn:math", element.uri);
        assertEquals(1, handler.emptyElementCount);
        assertSame(augmentations, handler.emptyElementAugmentations);
    }

    private static NamespaceBinder newConfiguredBinder() throws XMLConfigurationException {
        NamespaceBinder binder = new NamespaceBinder();
        binder.reset(new NamespaceBinderComponentManager());
        return binder;
    }

    public static class RecordingDocumentHandler extends DefaultFilter {
        final List<String> startedPrefixMappings = new ArrayList<>();
        final List<String> endedPrefixMappings = new ArrayList<>();
        int startElementCount;
        int emptyElementCount;
        int endElementCount;
        Augmentations startElementAugmentations;
        Augmentations emptyElementAugmentations;
        Augmentations endElementAugmentations;

        public void startPrefixMapping(String prefix, String uri) {
            startedPrefixMappings.add(prefix + "=" + uri);
        }

        public void endPrefixMapping(String prefix) {
            endedPrefixMappings.add(prefix);
        }

        @Override
        public void startElement(QName element, XMLAttributes attributes, Augmentations augmentations) {
            startElementCount++;
            startElementAugmentations = augmentations;
        }

        @Override
        public void emptyElement(QName element, XMLAttributes attributes, Augmentations augmentations) {
            emptyElementCount++;
            emptyElementAugmentations = augmentations;
        }

        @Override
        public void endElement(QName element, Augmentations augmentations) {
            endElementCount++;
            endElementAugmentations = augmentations;
        }
    }

    public static class NamespaceBinderComponentManager implements XMLComponentManager {
        @Override
        public boolean getFeature(String featureId) throws XMLConfigurationException {
            if (NAMESPACES.equals(featureId)) {
                return true;
            }
            if (OVERRIDE_NAMESPACES.equals(featureId) || INSERT_NAMESPACES.equals(featureId)) {
                return false;
            }
            throw new XMLConfigurationException(XMLConfigurationException.NOT_RECOGNIZED, featureId);
        }

        @Override
        public Object getProperty(String propertyId) throws XMLConfigurationException {
            if (NAMES_ELEMS.equals(propertyId) || NAMES_ATTRS.equals(propertyId)) {
                return "lower";
            }
            if (NAMESPACES_URI.equals(propertyId)) {
                return NamespaceBinder.XHTML_1_0_URI;
            }
            throw new XMLConfigurationException(XMLConfigurationException.NOT_RECOGNIZED, propertyId);
        }
    }
}
