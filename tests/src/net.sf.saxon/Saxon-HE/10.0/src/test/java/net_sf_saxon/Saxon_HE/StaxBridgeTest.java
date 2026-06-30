/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import com.ctc.wstx.stax.WstxInputFactory;

import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.pull.StaxBridge;
import net.sf.saxon.pull.UnparsedEntity;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import javax.xml.stream.XMLStreamReader;

import static org.assertj.core.api.Assertions.assertThat;

public class StaxBridgeTest {
    private static final String DOCUMENT_SYSTEM_ID = "https://example.test/documents/source.xml";

    @Test
    void reportsUnparsedEntitiesFromWoodstoxExtensionDeclarations() throws Exception {
        String xml = """
                <!DOCTYPE root [
                    <!NOTATION png SYSTEM "image/png">
                    <!ENTITY logo PUBLIC "-//Example//Logo//EN" "images/logo.png" NDATA png>
                ]>
                <root/>
                """;
        WstxInputFactory inputFactory = new WstxInputFactory();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(DOCUMENT_SYSTEM_ID, new StringReader(xml));
        StaxBridge bridge = new StaxBridge();
        bridge.setXMLStreamReader(reader);
        try {
            assertThat(bridge.next()).isEqualTo(PullProvider.START_DOCUMENT);
            assertThat(bridge.next()).isEqualTo(PullProvider.START_ELEMENT);

            List<UnparsedEntity> entities = bridge.getUnparsedEntities();

            assertThat(entities).hasSize(1);
            UnparsedEntity entity = entities.get(0);
            assertThat(entity.getName()).isEqualTo("logo");
            assertThat(entity.getPublicId()).isEqualTo("-//Example//Logo//EN");
            assertThat(entity.getSystemId()).endsWith("images/logo.png");
            assertThat(entity.getBaseURI()).isNotBlank();
        } finally {
            bridge.close();
        }
    }
}
