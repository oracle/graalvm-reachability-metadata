/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.sf.ehcache.constructs.web.PageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class PageInfoTest {
    @Test
    void compressesAndRestoresCachedResponseBody() throws Exception {
        byte[] body = "cached response body".getBytes(StandardCharsets.UTF_8);
        PageInfo page = new PageInfo(200, "text/plain", List.of(), List.of(), body, true);
        assertTrue(page.isOk());
        assertEquals("text/plain", page.getContentType());
        assertTrue(page.hasGzippedBody());
        assertTrue(PageInfo.isGzipped(page.getGzippedBody()));
        assertArrayEquals(body, page.getUngzippedBody());
        PageInfo plain = new PageInfo(200, "text/plain", List.of(), List.of(), body, false);
        assertArrayEquals(body, plain.getUngzippedBody());
        assertFalse(plain.hasGzippedBody());
    }
}
