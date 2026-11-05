/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletResponse;
import net.sf.ehcache.constructs.web.ResponseUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ResponseUtilTest {
    @Test
    void enforcesEmptyBodyStatusesAndMarksCompressedResponses() throws Exception {
        WebRequest request = new WebRequest();
        assertTrue(ResponseUtil.shouldBodyBeZero(request, HttpServletResponse.SC_NO_CONTENT));
        assertTrue(ResponseUtil.shouldBodyBeZero(request, HttpServletResponse.SC_NOT_MODIFIED));
        assertFalse(ResponseUtil.shouldBodyBeZero(request, HttpServletResponse.SC_OK));

        ByteArrayOutputStream empty = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(empty)) {
            gzip.finish();
        }
        assertTrue(ResponseUtil.shouldGzippedBodyBeZero(empty.toByteArray(), request));
        assertFalse(ResponseUtil.shouldGzippedBodyBeZero(new byte[0], request));
        WebResponse response = new WebResponse();
        ResponseUtil.addGzipHeader(response);
        assertEquals(List.of("gzip"), response.headers.get("Content-Encoding"));
    }
}
