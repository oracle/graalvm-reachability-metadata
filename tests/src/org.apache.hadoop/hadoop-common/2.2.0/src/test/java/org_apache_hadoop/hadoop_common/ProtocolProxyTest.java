/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.hadoop.ipc.ProtocolProxy;
import org.apache.hadoop.ipc.ProtocolSignature;
import org.apache.hadoop.ipc.VersionedProtocol;
import org.junit.jupiter.api.Test;

public class ProtocolProxyTest {
    private static final long PROTOCOL_VERSION = 13L;

    @Test
    void supportedMethodCheckFetchesServerSignatureFromVersionedProtocol() throws IOException {
        ProtocolSignature.resetCache();
        SampleServer server = new SampleServer();
        ProtocolProxy<SampleProtocol> protocolProxy = new ProtocolProxy<>(
                SampleProtocol.class, server, true);

        boolean supported = protocolProxy.isMethodSupported("echo", String.class);

        assertThat(supported).isTrue();
        assertThat(server.observedProtocol).isEqualTo(SampleProtocol.class.getName());
        assertThat(server.observedClientVersion).isEqualTo(PROTOCOL_VERSION);
        assertThat(server.observedClientMethodsHash).isNotZero();
        assertThat(server.signatureRequests).isEqualTo(1);
    }

    public interface SampleProtocol extends VersionedProtocol {
        long versionID = PROTOCOL_VERSION;

        String echo(String value) throws IOException;
    }

    public static class SampleServer implements SampleProtocol {
        private String observedProtocol;
        private long observedClientVersion;
        private int observedClientMethodsHash;
        private int signatureRequests;

        @Override
        public long getProtocolVersion(String protocol, long clientVersion) {
            return PROTOCOL_VERSION;
        }

        @Override
        public ProtocolSignature getProtocolSignature(
                String protocol, long clientVersion, int clientMethodsHash) throws IOException {
            observedProtocol = protocol;
            observedClientVersion = clientVersion;
            observedClientMethodsHash = clientMethodsHash;
            signatureRequests++;
            return ProtocolSignature.getProtocolSignature(
                    this, protocol, clientVersion, clientMethodsHash);
        }

        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }
}
