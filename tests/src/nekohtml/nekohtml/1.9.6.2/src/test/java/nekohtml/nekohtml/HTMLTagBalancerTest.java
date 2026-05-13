/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.xerces.util.AugmentationsImpl;
import org.apache.xerces.util.NamespaceSupport;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.XMLLocator;
import org.cyberneko.html.HTMLTagBalancer;
import org.cyberneko.html.filters.DefaultFilter;
import org.junit.jupiter.api.Test;

public class HTMLTagBalancerTest {
    @Test
    void forwardsStartDocumentToHandlerWithNamespaceContext() {
        HTMLTagBalancer tagBalancer = new HTMLTagBalancer();
        RecordingDocumentHandler handler = new RecordingDocumentHandler();
        NamespaceContext namespaceContext = new NamespaceSupport();
        Augmentations augmentations = new AugmentationsImpl();

        tagBalancer.setDocumentHandler(handler);
        tagBalancer.startDocument(null, "UTF-8", namespaceContext, augmentations);

        assertEquals(1, handler.startDocumentCount);
        assertEquals("UTF-8", handler.encoding);
        assertSame(namespaceContext, handler.namespaceContext);
        assertSame(augmentations, handler.startDocumentAugmentations);
    }

    @Test
    void forwardsPrefixMappingsToHandler() {
        HTMLTagBalancer tagBalancer = new HTMLTagBalancer();
        RecordingDocumentHandler handler = new RecordingDocumentHandler();
        Augmentations augmentations = new AugmentationsImpl();

        tagBalancer.setDocumentHandler(handler);
        tagBalancer.startPrefixMapping("svg", "http://www.w3.org/2000/svg", augmentations);
        tagBalancer.endPrefixMapping("svg", augmentations);

        assertEquals(1, handler.startPrefixMappingCount);
        assertEquals("svg", handler.prefix);
        assertEquals("http://www.w3.org/2000/svg", handler.uri);
        assertSame(augmentations, handler.startPrefixMappingAugmentations);
        assertEquals(1, handler.endPrefixMappingCount);
        assertEquals("svg", handler.endedPrefix);
        assertSame(augmentations, handler.endPrefixMappingAugmentations);
    }

    public static class RecordingDocumentHandler extends DefaultFilter {
        int startDocumentCount;
        String encoding;
        NamespaceContext namespaceContext;
        Augmentations startDocumentAugmentations;
        int startPrefixMappingCount;
        String prefix;
        String uri;
        Augmentations startPrefixMappingAugmentations;
        int endPrefixMappingCount;
        String endedPrefix;
        Augmentations endPrefixMappingAugmentations;

        @Override
        public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
                        Augmentations augmentations) {
            startDocumentCount++;
            this.encoding = encoding;
            this.namespaceContext = namespaceContext;
            this.startDocumentAugmentations = augmentations;
        }

        @Override
        public void startPrefixMapping(String prefix, String uri, Augmentations augmentations) {
            startPrefixMappingCount++;
            this.prefix = prefix;
            this.uri = uri;
            this.startPrefixMappingAugmentations = augmentations;
        }

        @Override
        public void endPrefixMapping(String prefix, Augmentations augmentations) {
            endPrefixMappingCount++;
            this.endedPrefix = prefix;
            this.endPrefixMappingAugmentations = augmentations;
        }
    }
}
