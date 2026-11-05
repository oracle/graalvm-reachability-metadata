/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import java.util.Set;

import org.eclipse.jetty.websocket.api.ExtensionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionConfigTest {
    @Test
    void parsesCompressionNegotiationParameters() {
        ExtensionConfig config = ExtensionConfig.parse(
                "permessage-deflate; client_max_window_bits=12; server_no_context_takeover");

        assertEquals("permessage-deflate", config.getName());
        assertEquals(12, config.getParameter("client_max_window_bits", 15));
        assertEquals(15, config.getParameter("server_max_window_bits", 15));
        assertEquals("default", config.getParameter("absent", "default"));
        assertEquals(Set.of("client_max_window_bits", "server_no_context_takeover"),
                config.getParameterKeys());
        assertTrue(config.getParameters().containsKey("server_no_context_takeover"));
        assertNull(config.getParameters().get("server_no_context_takeover"));
    }

    @Test
    void preservesUpdatedAndQuotedParametersWhenRenderedAndReparsed() {
        ExtensionConfig config = ExtensionConfig.parse("example; mode=initial");
        config.setParameter("mode", "two words; separated");
        config.setParameter("window", 10);
        config.setParameter("enabled");

        ExtensionConfig reparsed = ExtensionConfig.parse(config.getParameterizedName());
        assertEquals("example", reparsed.getName());
        assertEquals("two words; separated", reparsed.getParameter("mode", ""));
        assertEquals(10, reparsed.getParameter("window", 0));
        assertEquals(config.getParameters(), reparsed.getParameters());
    }
}
