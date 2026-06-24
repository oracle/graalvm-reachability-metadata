/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.hadoop.ipc.ProtocolSignature;
import org.apache.hadoop.ipc.VersionedProtocol;
import org.junit.jupiter.api.Test;

public class ProtocolSignatureTest {
    private static final long SERVER_VERSION = 7L;
    private static final long CLIENT_VERSION = 3L;
    private static final int CLIENT_METHODS_HASH = 1;

    @Test
    void protocolNameLookupBuildsSignatureFromProtocolMethods() throws ClassNotFoundException {
        ProtocolSignature.resetCache();

        ProtocolSignature signature = ProtocolSignature.getProtocolSignature(
                SampleProtocol.class.getName(), SERVER_VERSION);

        assertThat(signature.getVersion()).isEqualTo(SERVER_VERSION);
        assertThat(signature.getMethods()).isNotNull().isNotEmpty();
    }

    @Test
    void serverLookupLoadsProtocolNameAndUsesServerVersion() throws IOException {
        ProtocolSignature.resetCache();
        SampleServer server = new SampleServer();

        ProtocolSignature signature = ProtocolSignature.getProtocolSignature(
                server, SampleProtocol.class.getName(), CLIENT_VERSION, CLIENT_METHODS_HASH);

        assertThat(signature.getVersion()).isEqualTo(SERVER_VERSION);
        assertThat(signature.getMethods()).isNotNull().isNotEmpty();
        assertThat(server.observedProtocol).isEqualTo(SampleProtocol.class.getName());
        assertThat(server.observedClientVersion).isEqualTo(CLIENT_VERSION);
    }

    public interface SampleProtocol extends VersionedProtocol {
        String echo(String value) throws IOException;

        int add(int left, int right) throws IOException;
    }

    public static class SampleServer implements SampleProtocol {
        private String observedProtocol;
        private long observedClientVersion;

        @Override
        public long getProtocolVersion(String protocol, long clientVersion) {
            observedProtocol = protocol;
            observedClientVersion = clientVersion;
            return SERVER_VERSION;
        }

        @Override
        public ProtocolSignature getProtocolSignature(
                String protocol, long clientVersion, int clientMethodsHash) throws IOException {
            return ProtocolSignature.getProtocolSignature(
                    this, protocol, clientVersion, clientMethodsHash);
        }

        @Override
        public String echo(String value) {
            return "echo:" + value;
        }

        @Override
        public int add(int left, int right) {
            return left + right;
        }
    }
}
