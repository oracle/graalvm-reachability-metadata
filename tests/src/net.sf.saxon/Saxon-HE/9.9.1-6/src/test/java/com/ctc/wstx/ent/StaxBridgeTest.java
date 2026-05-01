/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ctc.wstx.ent;

import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.util.StreamReaderDelegate;

import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.pull.StaxBridge;
import net.sf.saxon.pull.UnparsedEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StaxBridgeTest {
    @Test
    void getUnparsedEntitiesReadsLegacyWoodstoxEntity() throws Exception {
        UnparsedExtEntity legacyWoodstoxEntity = new UnparsedExtEntity(
                "logo",
                "images/logo.gif",
                "-//example//logo",
                "https://example.test/xml/catalog.xml");
        StaxBridge bridge = new StaxBridge();
        bridge.setXMLStreamReader(new DtdThenEndDocumentReader(List.<Object>of(legacyWoodstoxEntity)));

        assertThat(bridge.next()).isEqualTo(PullProvider.START_DOCUMENT);
        assertThat(bridge.next()).isEqualTo(PullProvider.END_DOCUMENT);

        List<UnparsedEntity> entities = bridge.getUnparsedEntities();
        assertThat(entities).hasSize(1);
        UnparsedEntity entity = entities.get(0);
        assertThat(entity.getName()).isEqualTo("logo");
        assertThat(entity.getSystemId()).isEqualTo("https://example.test/xml/images/logo.gif");
        assertThat(entity.getPublicId()).isEqualTo("-//example//logo");
        assertThat(entity.getBaseURI()).isEqualTo("https://example.test/xml/catalog.xml");
    }

    public static class WoodstoxEntityMethods {
        private final String name;
        private final String systemId;
        private final String publicId;
        private final String baseURI;

        public WoodstoxEntityMethods(String name, String systemId, String publicId, String baseURI) {
            this.name = name;
            this.systemId = systemId;
            this.publicId = publicId;
            this.baseURI = baseURI;
        }

        public String getName() {
            return name;
        }

        public String getSystemId() {
            return systemId;
        }

        public String getPublicId() {
            return publicId;
        }

        public String getBaseURI() {
            return baseURI;
        }
    }

    private static final class DtdThenEndDocumentReader extends StreamReaderDelegate {
        private final List<Object> unparsedEntities;
        private int eventIndex = -1;

        private DtdThenEndDocumentReader(List<Object> unparsedEntities) {
            this.unparsedEntities = unparsedEntities;
        }

        @Override
        public boolean hasNext() {
            return eventIndex < 1;
        }

        @Override
        public int next() throws XMLStreamException {
            eventIndex++;
            if (eventIndex == 0) {
                return XMLStreamConstants.DTD;
            }
            return XMLStreamConstants.END_DOCUMENT;
        }

        @Override
        public Object getProperty(String name) {
            if ("javax.xml.stream.entities".equals(name)) {
                return unparsedEntities;
            }
            return null;
        }

        @Override
        public void close() throws XMLStreamException {
        }
    }
}

class UnparsedExtEntity extends StaxBridgeTest.WoodstoxEntityMethods {
    UnparsedExtEntity(String name, String systemId, String publicId, String baseURI) {
        super(name, systemId, publicId, baseURI);
    }
}
