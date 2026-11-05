/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.servlet.Filter;
import net.sf.ehcache.constructs.web.filter.GzipFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class GzipFilterTest {
    @Test
    void compressesResponsesForClientsAcceptingGzip() throws Exception {
        Filter filter = new GzipFilter();
        filter.init(new WebContext());
        try {
            WebRequest request = new WebRequest();
            request.headers.put("Accept-Encoding", "gzip, deflate");
            WebResponse response = new WebResponse();
            filter.doFilter(request, response, (incoming, outgoing) -> {
                outgoing.setContentType("text/plain");
                outgoing.getWriter().write("compressed order summary");
            });
            assertEquals(List.of("gzip"), response.headers.get("Content-Encoding"));
            assertEquals("text/plain", response.contentType);
            assertEquals(response.body.size(), response.contentLength);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(response.body.toByteArray()))) {
                assertEquals("compressed order summary", new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertTrue(response.isCommitted());
        } finally {
            filter.destroy();
        }
    }

    @Test
    void leavesUncompressedClientsAndIncludesUnchanged() throws Exception {
        Filter filter = new GzipFilter();
        filter.init(new WebContext());
        try {
            for (boolean included : List.of(false, true)) {
                WebRequest request = new WebRequest();
                if (included) {
                    request.headers.put("Accept-Encoding", "gzip");
                    request.setAttribute("javax.servlet.include.request_uri", "/fragment");
                }
                WebResponse response = new WebResponse();
                filter.doFilter(request, response, (incoming, outgoing) -> outgoing.getWriter().write("plain summary"));
                assertEquals("plain summary", response.body.toString(StandardCharsets.UTF_8));
                assertFalse(response.containsHeader("Content-Encoding"));
            }
        } finally {
            filter.destroy();
        }
    }
}
