/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class ExpectContinueMethodTest {
    @Test
    void sendsBodyAfterServerAcceptsContinueExpectation() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("accepted")) {
            PostMethod method = new PostMethod(server.url("/continue"));
            method.setUseExpectHeader(true);
            method.setRequestEntity(new StringRequestEntity("expected payload", "text/plain", "UTF-8"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("accepted", method.getResponseBodyAsString());
                String request = server.request();
                assertTrue(request.contains("Expect: 100-continue"));
                assertTrue(request.endsWith("expected payload"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
