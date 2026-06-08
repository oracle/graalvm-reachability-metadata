/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtocolSignature;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.VersionedProtocol;
import org.apache.hadoop.ipc.WritableRpcEngine;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Test;

public class WritableRpcEngineInnerServerInnerWritableRpcInvokerTest {
    private static final long PROTOCOL_VERSION = 1L;

    @Test
    void writableRpcServerInvokesProtocolMethodThroughProxy() throws IOException {
        Configuration conf = new Configuration(false);
        RPC.setProtocolEngine(conf, WritableEchoProtocol.class, WritableRpcEngine.class);
        RPC.Server server = new RPC.Builder(conf)
                .setProtocol(WritableEchoProtocol.class)
                .setInstance(new WritableEchoServer())
                .setBindAddress("127.0.0.1")
                .setPort(0)
                .setNumHandlers(1)
                .build();
        WritableEchoProtocol proxy = null;
        try {
            server.start();
            proxy = RPC.getProxy(
                    WritableEchoProtocol.class,
                    PROTOCOL_VERSION,
                    NetUtils.getConnectAddress(server),
                    UserGroupInformation.createRemoteUser("writable-rpc-client"),
                    conf,
                    NetUtils.getDefaultSocketFactory(conf));

            String result = proxy.echo("hadoop");

            assertThat(result).isEqualTo("echo:hadoop");
        } finally {
            if (proxy != null) {
                RPC.stopProxy(proxy);
            }
            server.stop();
        }
    }

    public interface WritableEchoProtocol extends VersionedProtocol {
        long versionID = PROTOCOL_VERSION;

        String echo(String value) throws IOException;
    }

    public static class WritableEchoServer implements WritableEchoProtocol {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }

        @Override
        public long getProtocolVersion(String protocol, long clientVersion) {
            return PROTOCOL_VERSION;
        }

        @Override
        public ProtocolSignature getProtocolSignature(
                String protocol, long clientVersion, int clientMethodsHash) throws IOException {
            return ProtocolSignature.getProtocolSignature(
                    this, protocol, clientVersion, clientMethodsHash);
        }
    }
}
