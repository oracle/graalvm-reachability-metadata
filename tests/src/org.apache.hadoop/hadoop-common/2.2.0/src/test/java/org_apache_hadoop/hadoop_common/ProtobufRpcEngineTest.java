/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import javax.net.SocketFactory;

import com.google.protobuf.BlockingService;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtocolInfo;
import org.apache.hadoop.ipc.ProtocolProxy;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.RpcClientUtil;
import org.apache.hadoop.ipc.protobuf.TestProtos.EchoRequestProto;
import org.apache.hadoop.ipc.protobuf.TestProtos.EchoResponseProto;
import org.apache.hadoop.ipc.protobuf.TestProtos.EmptyRequestProto;
import org.apache.hadoop.ipc.protobuf.TestProtos.EmptyResponseProto;
import org.apache.hadoop.ipc.protobuf.TestRpcServiceProtos.TestProtobufRpcProto;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Test;

public class ProtobufRpcEngineTest {
    private static final long PROTOCOL_VERSION = 1L;

    @Test
    void directEngineCreatesProtocolProxy() throws IOException {
        Configuration conf = new Configuration(false);
        RPC.setProtocolEngine(conf, TestProtocol.class, ProtobufRpcEngine.class);
        ProtobufRpcEngine engine = new ProtobufRpcEngine();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 1);
        SocketFactory socketFactory = NetUtils.getDefaultSocketFactory(conf);
        ProtocolProxy<TestProtocol> protocolProxy = engine.getProxy(
                TestProtocol.class,
                PROTOCOL_VERSION,
                address,
                UserGroupInformation.createRemoteUser("protobuf-rpc-client"),
                conf,
                socketFactory,
                1);
        TestProtocol proxy = protocolProxy.getProxy();

        try {
            assertThat(proxy).isInstanceOf(TestProtocol.class);
        } finally {
            RPC.stopProxy(proxy);
        }
    }

    @Test
    void rpcClientUtilityUsesProtocolMetaInfoProxy() throws IOException {
        Configuration conf = new Configuration(false);
        RPC.setProtocolEngine(conf, TestProtocol.class, ProtobufRpcEngine.class);
        BlockingService service = TestProtobufRpcProto.newReflectiveBlockingService(
                new TestProtocolImpl());
        RPC.Server server = new RPC.Builder(conf)
                .setProtocol(TestProtocol.class)
                .setInstance(service)
                .setBindAddress("127.0.0.1")
                .setPort(0)
                .setNumHandlers(1)
                .build();
        TestProtocol proxy = null;
        try {
            server.start();
            proxy = RPC.getProxy(
                    TestProtocol.class,
                    PROTOCOL_VERSION,
                    NetUtils.getConnectAddress(server),
                    UserGroupInformation.createRemoteUser("protobuf-rpc-client"),
                    conf,
                    NetUtils.getDefaultSocketFactory(conf));

            boolean supported = RpcClientUtil.isMethodSupported(
                    proxy,
                    TestProtocol.class,
                    RPC.RpcKind.RPC_PROTOCOL_BUFFER,
                    PROTOCOL_VERSION,
                    "ping");

            assertThat(supported).isTrue();
        } finally {
            if (proxy != null) {
                RPC.stopProxy(proxy);
            }
            server.stop();
        }
    }

    @ProtocolInfo(
            protocolName = "org_apache_hadoop.hadoop_common.ProtobufRpcEngineTest.TestProtocol",
            protocolVersion = PROTOCOL_VERSION)
    public interface TestProtocol extends TestProtobufRpcProto.BlockingInterface {
    }

    private static final class TestProtocolImpl implements TestProtocol {
        @Override
        public EmptyResponseProto ping(RpcController controller, EmptyRequestProto request) {
            return EmptyResponseProto.getDefaultInstance();
        }

        @Override
        public EchoResponseProto echo(RpcController controller, EchoRequestProto request) {
            return EchoResponseProto.newBuilder().setMessage(request.getMessage()).build();
        }

        @Override
        public EmptyResponseProto error(RpcController controller, EmptyRequestProto request)
                throws ServiceException {
            throw new ServiceException("error");
        }

        @Override
        public EmptyResponseProto error2(RpcController controller, EmptyRequestProto request)
                throws ServiceException {
            throw new ServiceException("error2");
        }
    }
}
