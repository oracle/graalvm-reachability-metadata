/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.netty.channel.Channel;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.protocol.AbstractMessage;
import org.apache.seata.core.rpc.netty.AbstractNettyRemotingClient;
import org.apache.seata.core.rpc.netty.NettyClientConfig;
import org.apache.seata.core.rpc.netty.NettyPoolKey;
import org.junit.jupiter.api.Test;

public class AbstractNettyRemotingClientTest {
    static {
        System.setProperty("config.type", "file");
        System.setProperty("config.file.name", "file.conf");
        System.setProperty("transport.type", "TCP");
        System.setProperty("transport.server", "NIO");
        ConfigurationFactory.reload();
    }

    @Test
    void getXidReadsDeclaredXidFieldFromGenericMessage() {
        ProbeNettyRemotingClient client = new ProbeNettyRemotingClient();
        try {
            GenericTransactionMessage message = new GenericTransactionMessage("generic-xid");

            String xid = client.extractXid(message);

            assertThat(xid).isEqualTo("generic-xid");
        } finally {
            client.destroy();
        }
    }

    public static class GenericTransactionMessage {
        public final String xid;

        GenericTransactionMessage(String xid) {
            this.xid = xid;
        }
    }

    private static class ProbeNettyRemotingClient extends AbstractNettyRemotingClient {
        ProbeNettyRemotingClient() {
            super(new NettyClientConfig(), null, new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>()), NettyPoolKey.TransactionRole.TMROLE);
        }

        String extractXid(Object message) {
            return getXid(message);
        }

        @Override
        protected Function<String, NettyPoolKey> getPoolKeyFunction() {
            return serverAddress -> new NettyPoolKey(NettyPoolKey.TransactionRole.TMROLE, serverAddress);
        }

        @Override
        protected String getTransactionServiceGroup() {
            return "test-service-group";
        }

        @Override
        protected boolean isEnableClientBatchSendRequest() {
            return false;
        }

        @Override
        protected long getRpcRequestTimeout() {
            return TimeUnit.SECONDS.toMillis(1);
        }

        @Override
        public void onRegisterMsgSuccess(String serverAddress, Channel channel, Object response,
                AbstractMessage requestMessage) {
            throw new UnsupportedOperationException("Registration callbacks are not used by this test client.");
        }

        @Override
        public void onRegisterMsgFail(String serverAddress, Channel channel, Object response,
                AbstractMessage requestMessage) {
            throw new UnsupportedOperationException("Registration callbacks are not used by this test client.");
        }
    }
}
