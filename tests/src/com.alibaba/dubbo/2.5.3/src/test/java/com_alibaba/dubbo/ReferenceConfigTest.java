/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import org.junit.jupiter.api.Test;

public class ReferenceConfigTest {
    private static boolean directProtocolInstalled;

    @Test
    void refersToAServiceConfiguredByInterfaceName() {
        installDirectReferenceProtocol();
        ReferenceConfig<Echo> reference = new ReferenceConfig<Echo>();
        reference.setApplication(new ApplicationConfig("reference-config-test"));
        reference.setUrl("reference-test://127.0.0.1");
        reference.setProxy("reference-test");
        reference.setInterface(Echo.class.getName());

        try {
            Echo echo = reference.get();

            assertThat(echo.echo("dubbo")).isEqualTo("echo:dubbo");
            assertThat(reference.getInterfaceClass()).isEqualTo(Echo.class);
        } finally {
            reference.destroy();
        }
    }

    static synchronized void installDirectReferenceProtocol() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
        if (!directProtocolInstalled) {
            ExtensionLoader.getExtensionLoader(Protocol.class)
                    .addExtension("reference-test", DirectProtocol.class);
            ExtensionLoader.getExtensionLoader(ProxyFactory.class)
                    .addExtension("reference-test", DirectProxyFactory.class);
            directProtocolInstalled = true;
        }
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoClient implements Echo {
        private final Invoker<Echo> invoker;

        EchoClient(Invoker<Echo> invoker) {
            this.invoker = invoker;
        }

        @Override
        public String echo(String value) {
            Result result =
                    invoker.invoke(
                            new RpcInvocation(
                                    "echo", new Class<?>[] {String.class}, new Object[] {value}));
            return (String) result.getValue();
        }
    }

    public static class DirectProxyFactory implements ProxyFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getProxy(Invoker<T> invoker) throws RpcException {
            return (T) new EchoClient((Invoker<Echo>) invoker);
        }

        @Override
        public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException {
            throw new UnsupportedOperationException("provider proxies are not used by this factory");
        }
    }

    public static class DirectProtocol implements Protocol {
        @Override
        public int getDefaultPort() {
            return 0;
        }

        @Override
        public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
            throw new UnsupportedOperationException("export is not used by this protocol");
        }

        @Override
        public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
            return new DirectInvoker<T>(type, url);
        }

        @Override
        public void destroy() { }
    }

    private static class DirectInvoker<T> implements Invoker<T> {
        private final Class<T> type;
        private final URL url;

        DirectInvoker(Class<T> type, URL url) {
            this.type = type;
            this.url = url;
        }

        @Override
        public Class<T> getInterface() {
            return type;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
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
    }
}
