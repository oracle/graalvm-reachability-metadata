/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TelnetTestSupport {
    private TelnetTestSupport() { }

    static ExportedService exportEchoService() {
        DubboProtocol protocol = new DubboProtocol();
        EchoInvoker invoker = new EchoInvoker();
        Exporter<Echo> exporter = protocol.export(invoker);
        return new ExportedService(protocol, exporter, invoker);
    }

    public interface Echo {
        String echo(String value);
    }

    static final class ExportedService implements AutoCloseable {
        private final DubboProtocol protocol;
        private final Exporter<Echo> exporter;
        private final EchoInvoker invoker;

        ExportedService(DubboProtocol protocol, Exporter<Echo> exporter, EchoInvoker invoker) {
            this.protocol = protocol;
            this.exporter = exporter;
            this.invoker = invoker;
        }

        EchoInvoker getInvoker() {
            return invoker;
        }

        @Override
        public void close() {
            exporter.unexport();
            protocol.destroy();
        }
    }

    static final class EchoInvoker implements Invoker<Echo> {
        private final URL url =
                new URL(
                        "dubbo",
                        "127.0.0.1",
                        0,
                        Echo.class.getName(),
                        "isserver",
                        "false");
        private Invocation invocation;

        @Override
        public Class<Echo> getInterface() {
            return Echo.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            this.invocation = invocation;
            return new RpcResult("echo:" + invocation.getArguments()[0]);
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() { }

        Invocation getInvocation() {
            return invocation;
        }
    }

    static final class RecordingChannel implements Channel {
        private final URL url = new URL("telnet", "127.0.0.1", 0);
        private final InetSocketAddress local = new InetSocketAddress("127.0.0.1", 20000);
        private final InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 20001);
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final List<Object> messages = Collections.synchronizedList(new ArrayList<Object>());
        private final CountDownLatch sends;
        private boolean closed;

        RecordingChannel(int expectedSends) {
            sends = new CountDownLatch(expectedSends);
        }

        boolean awaitSends() throws InterruptedException {
            return sends.await(10, TimeUnit.SECONDS);
        }

        List<Object> getMessages() {
            return messages;
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public ChannelHandler getChannelHandler() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return local;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remote;
        }

        @Override
        public void send(Object message) throws RemotingException {
            messages.add(message);
            sends.countDown();
        }

        @Override
        public void send(Object message, boolean sent) throws RemotingException {
            send(message);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void close(int timeout) {
            close();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean isConnected() {
            return !closed;
        }

        @Override
        public boolean hasAttribute(String key) {
            return attributes.containsKey(key);
        }

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void removeAttribute(String key) {
            attributes.remove(key);
        }
    }
}
