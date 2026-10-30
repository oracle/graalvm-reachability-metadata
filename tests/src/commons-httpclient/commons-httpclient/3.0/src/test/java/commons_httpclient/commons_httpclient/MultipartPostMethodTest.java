/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.MultipartPostMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class MultipartPostMethodTest {
    @Test
    void submitsMultipartForm() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("uploaded")) {
            MultipartPostMethod method = new MultipartPostMethod(server.url("/multipart"));
            method.addParameter("message", "multipart value");
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("uploaded", method.getResponseBodyAsString());
                String request = server.request();
                assertTrue(request.startsWith("POST /multipart HTTP/1.1"));
                assertTrue(request.contains("multipart/form-data; boundary="));
                assertTrue(request.contains("name=\"message\""));
                assertTrue(request.contains("multipart value"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
