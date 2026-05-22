/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jgroups.demos.RelayDemoRpc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class RelayDemoRpcTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startsInteractiveRpcDemoWithLoopbackStack() throws Exception {
        Path stackConfiguration = writeLoopbackStackConfiguration();
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TestableRelayDemoRpc demo = new TestableRelayDemoRpc();
        Throwable thrown;
        String localAddress;

        try {
            System.setIn(new ScriptedInputStream("help\n"));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            thrown = catchThrowable(() -> demo.start(stackConfiguration.toString(), "relay-demo-rpc-test"));
            localAddress = demo.localAddress();
        } finally {
            demo.closeResources();
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        assertThat(thrown).isInstanceOf(IOException.class)
                .hasMessage("scripted input exhausted");
        assertThat(localAddress).isNotBlank();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("unicast <text>", "mcast <site>+");
    }

    private Path writeLoopbackStackConfiguration() throws IOException {
        Path configuration = temporaryDirectory.resolve("relay-demo-rpc-loopback.xml");
        Files.writeString(configuration, """
                <config>
                    <SHARED_LOOPBACK/>
                    <SHARED_LOOPBACK_PING/>
                    <pbcast.NAKACK2/>
                    <UNICAST3/>
                    <pbcast.STABLE/>
                    <pbcast.GMS join_timeout=\"1000\"/>
                    <FRAG2 frag_size=\"8000\"/>
                </config>
                """, StandardCharsets.UTF_8);
        return configuration;
    }

    public static class TestableRelayDemoRpc extends RelayDemoRpc {
        String localAddress() {
            return local_addr;
        }

        void closeResources() throws IOException {
            if(disp != null) {
                disp.close();
            }
            if(ch != null) {
                ch.close();
            }
        }
    }

    private static final class ScriptedInputStream extends InputStream {
        private final byte[] input;
        private int index;

        private ScriptedInputStream(String input) {
            this.input = input.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() throws IOException {
            if(index < input.length) {
                return input[index++];
            }
            throw new IOException("scripted input exhausted");
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public long skip(long n) {
            return 0;
        }
    }
}
