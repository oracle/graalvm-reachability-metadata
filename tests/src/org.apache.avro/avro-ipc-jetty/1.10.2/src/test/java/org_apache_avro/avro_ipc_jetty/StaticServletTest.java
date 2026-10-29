/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro_ipc_jetty;

import org.apache.avro.ipc.jetty.StaticServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class StaticServletTest {
    private final StaticServlet servlet = new StaticServlet();

    @Test
    void loadsBundledStylesheetUsingOnlyTheFinalPathComponent() throws IOException {
        String stylesheet = readAsset("/avro.css");
        assertTrue(stylesheet.contains("table#charts_table tr"));
        assertEquals(stylesheet, readAsset("/stats/static/avro.css"));
        assertEquals(stylesheet, readAsset("/../../avro.css"));
    }

    @Test
    void loadsBundledChartJavascript() throws IOException {
        String javascript = readAsset("/static/avro.js");
        assertTrue(javascript.contains("function makeDotChart("));
        assertTrue(javascript.contains("function makeBarChart("));
    }

    @Test
    void returnsNoResourceForAnUnknownAsset() {
        assertNull(servlet.getResource("/static/nonexistent-chart.css"));
    }

    private String readAsset(String path) throws IOException {
        try (Resource resource = servlet.getResource(path)) {
            assertNotNull(resource);
            assertTrue(resource.exists());
            assertFalse(resource.isDirectory());
            try (InputStream input = resource.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
