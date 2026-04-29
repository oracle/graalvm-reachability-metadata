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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RelayDemoRpcTest {
    private static final String TEST_STACK = "SHARED_LOOPBACK:" +
        "SHARED_LOOPBACK_PING:" +
        "pbcast.NAKACK2:" +
        "UNICAST3:" +
        "pbcast.STABLE:" +
        "pbcast.GMS(join_timeout=1000):" +
        "FRAG2(frag_size=8000)";

    @Test
    void startBuildsRpcMethodCallBeforeReadingConsoleInput() {
        InputStream originalInput = System.in;
        RelayDemoRpcHarness demo = new RelayDemoRpcHarness();
        System.setIn(new ByteArrayInputStream(new byte[0]));
        try {
            assertThatThrownBy(() -> demo.start(TEST_STACK, "relay-demo-rpc-test"))
                .isInstanceOf(NullPointerException.class);
            assertThat(demo.localAddress()).isNotBlank();
        }
        finally {
            demo.close();
            System.setIn(originalInput);
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
