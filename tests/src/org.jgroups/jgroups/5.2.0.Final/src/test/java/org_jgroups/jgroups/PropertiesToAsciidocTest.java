/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.TCPGOSSIP;
import org.jgroups.util.PropertiesToAsciidoc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesToAsciidocTest extends PropertiesToAsciidoc {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void readsAnnotatedFieldAndMethodDescriptionsForProtocolComponent() throws Exception {
        Map<String, String> descriptions = new TreeMap<>();

        getDescriptions(TCPGOSSIP.class, descriptions, null, false);

        assertThat(descriptions)
                .containsEntry("sock_conn_timeout", "Max time for socket creation. Default is 1000 ms")
                .containsEntry("reconnect_interval",
                        "Interval (ms) by which a disconnected stub attempts to reconnect to the GossipRouter")
                .containsEntry("initial_hosts", "Comma delimited list of hosts to be contacted for initial membership")
                .containsEntry("use_nio", "Whether to use blocking (false) or non-blocking (true) connections. "
                        + "If GossipRouter is used, this needs to be false; if GossipRouterNio is used, "
                        + "it needs to be true");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
