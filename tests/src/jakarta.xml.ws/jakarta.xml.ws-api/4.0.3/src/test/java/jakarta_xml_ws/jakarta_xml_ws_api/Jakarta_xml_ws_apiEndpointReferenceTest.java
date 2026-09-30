/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_ws.jakarta_xml_ws_api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import jakarta.xml.ws.EndpointReference;
import jakarta.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Test;

public class Jakarta_xml_ws_apiEndpointReferenceTest {
    @Test
    void writesW3cEndpointReferenceWithAddressAndParameters() {
        String endpointReferenceXml = """
                <wsa:EndpointReference xmlns:wsa="http://www.w3.org/2005/08/addressing">
                  <wsa:Address>https://example.test/orders</wsa:Address>
                  <wsa:ReferenceParameters>
                    <request:tenant xmlns:request="urn:example:request">acme</request:tenant>
                  </wsa:ReferenceParameters>
                </wsa:EndpointReference>
                """;

        EndpointReference endpointReference = new W3CEndpointReference(
                new StreamSource(new StringReader(endpointReferenceXml)));
        StringWriter serialized = new StringWriter();

        endpointReference.writeTo(new StreamResult(serialized));

        assertTrue(serialized.toString().contains("https://example.test/orders"));
        assertTrue(serialized.toString().contains("urn:example:request"));
        assertTrue(serialized.toString().contains(">acme</"));
    }
}
