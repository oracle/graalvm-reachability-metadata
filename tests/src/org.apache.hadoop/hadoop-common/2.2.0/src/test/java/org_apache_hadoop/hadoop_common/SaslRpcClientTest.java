/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.ipc.ProtobufRpcEngine.RpcResponseMessageWrapper;
import org.apache.hadoop.ipc.Server.AuthProtocol;
import org.apache.hadoop.ipc.protobuf.RpcHeaderProtos.RpcResponseHeaderProto;
import org.apache.hadoop.ipc.protobuf.RpcHeaderProtos.RpcResponseHeaderProto.RpcStatusProto;
import org.apache.hadoop.ipc.protobuf.RpcHeaderProtos.RpcSaslProto;
import org.apache.hadoop.ipc.protobuf.RpcHeaderProtos.RpcSaslProto.SaslAuth;
import org.apache.hadoop.ipc.protobuf.RpcHeaderProtos.RpcSaslProto.SaslState;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.AnnotatedSecurityInfo;
import org.apache.hadoop.security.SaslRpcClient;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.security.token.TokenInfo;
import org.apache.hadoop.security.token.TokenSelector;
import org.junit.jupiter.api.Test;

public class SaslRpcClientTest {
    private static final InetSocketAddress SERVER_ADDRESS =
            new InetSocketAddress("127.0.0.1", 8020);

    @Test
    void tokenNegotiationInstantiatesProtocolTokenSelector() throws IOException {
        CountingTokenSelector.INSTANTIATIONS.set(0);
        SecurityUtil.setSecurityInfoProviders(new AnnotatedSecurityInfo());
        try {
            SaslRpcClient client = new SaslRpcClient(
                    UserGroupInformation.createRemoteUser("token-user"),
                    TokenProtocol.class,
                    SERVER_ADDRESS,
                    new Configuration(false));

            assertThatThrownBy(() -> client.saslConnect(
                    tokenNegotiateResponse(), new ByteArrayOutputStream()))
                    .isInstanceOf(AccessControlException.class)
                    .hasMessageContaining("Client cannot authenticate via:[TOKEN]");

            assertThat(CountingTokenSelector.INSTANTIATIONS).hasValue(1);
        } finally {
            SecurityUtil.setSecurityInfoProviders();
        }
    }

    private static ByteArrayInputStream tokenNegotiateResponse() throws IOException {
        RpcSaslProto negotiate = RpcSaslProto.newBuilder()
                .setState(SaslState.NEGOTIATE)
                .addAuths(SaslAuth.newBuilder()
                        .setMethod("TOKEN")
                        .setMechanism("DIGEST-MD5")
                        .setProtocol("")
                        .setServerId("default"))
                .build();
        RpcResponseHeaderProto header = RpcResponseHeaderProto.newBuilder()
                .setCallId(AuthProtocol.SASL.callId)
                .setStatus(RpcStatusProto.SUCCESS)
                .build();
        RpcResponseMessageWrapper response = new RpcResponseMessageWrapper(header, negotiate);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(response.getLength());
        response.write(out);
        out.flush();
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    @TokenInfo(CountingTokenSelector.class)
    public interface TokenProtocol {
    }

    public static class CountingTokenSelector implements TokenSelector<TokenIdentifier> {
        private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();

        public CountingTokenSelector() {
            INSTANTIATIONS.incrementAndGet();
        }

        @Override
        public Token<TokenIdentifier> selectToken(
                Text service,
                Collection<Token<? extends TokenIdentifier>> tokens) {
            return null;
        }
    }
}
