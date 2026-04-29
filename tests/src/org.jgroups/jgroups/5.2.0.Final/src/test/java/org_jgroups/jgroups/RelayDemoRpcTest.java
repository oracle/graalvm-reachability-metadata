/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.demos.RelayDemoRpc;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RelayDemoRpcTest {
    private static final String TEST_STACK = """
        <config xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="urn:org:jgroups"
                xsi:schemaLocation="urn:org:jgroups http://www.jgroups.org/schema/jgroups.xsd">
            <SHARED_LOOPBACK/>
            <SHARED_LOOPBACK_PING/>
            <pbcast.NAKACK2/>
            <UNICAST3/>
            <pbcast.STABLE/>
            <pbcast.GMS join_timeout="1000"/>
            <FRAG2 frag_size="8000"/>
        </config>
        """;

    @Test
    void startBuildsRpcMethodCallBeforeReadingConsoleInput() throws Exception {
        InputStream originalInput = System.in;
        RelayDemoRpcHarness demo = new RelayDemoRpcHarness();
        Path stackFile = Files.writeString(
            Files.createTempFile("relay-demo-rpc", ".xml"), TEST_STACK, StandardCharsets.UTF_8);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        try {
            assertThatThrownBy(() -> demo.start(stackFile.toString(), "relay-demo-rpc-test"))
                .isInstanceOf(NullPointerException.class);
            assertThat(demo.localAddress()).isNotBlank();
        }
        finally {
            demo.close();
            System.setIn(originalInput);
            Files.deleteIfExists(stackFile);
        }
    }

    private static final class RelayDemoRpcHarness extends RelayDemoRpc {
        String localAddress() {
            return local_addr;
        }

        void close() {
            if(disp != null)
                disp.stop();
            if(ch != null)
                ch.close();
        }
    }
}
