/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ArgumentConfig;
import com.alibaba.dubbo.config.MethodConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.Directory;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ServiceConfigTest {
    private static boolean adaptiveExtensionsInstalled;

    @BeforeAll
    static synchronized void installStaticallyRepresentableAdaptiveExtensions() {
        if (adaptiveExtensionsInstalled) {
            return;
        }
        ExtensionLoader.getExtensionLoader(Protocol.class)
                .addExtension("static-adaptive", StaticAdaptiveProtocol.class);
        ExtensionLoader.getExtensionLoader(ProxyFactory.class)
                .addExtension("static-adaptive", StaticAdaptiveProxyFactory.class);
        ExtensionLoader.getExtensionLoader(Cluster.class)
                .addExtension("static-adaptive", StaticAdaptiveCluster.class);
        adaptiveExtensionsInstalled = true;
    }

    @Test
    void resolvesAnInterfaceConfiguredByName() {
        ServiceConfig<Echo> service = new ServiceConfig<Echo>();
        service.setInterface(Echo.class.getName());

        assertThat(service.getInterfaceClass()).isEqualTo(Echo.class);
        assertThat(service.getInterface()).isEqualTo(Echo.class.getName());
    }

    @Test
    void rejectsAnArgumentWhoseIndexAndTypeDisagree() {
        ArgumentConfig argument = new ArgumentConfig();
        argument.setIndex(0);
        argument.setType(Integer.class.getName());

        MethodConfig method = new MethodConfig();
        method.setName("echo");
        method.setArguments(Collections.singletonList(argument));

        ProtocolConfig protocol = new ProtocolConfig("injvm", 1);
        protocol.setHost("127.0.0.1");

        ServiceConfig<Echo> service = new ServiceConfig<Echo>();
        service.setApplication(new ApplicationConfig("service-config-test"));
        service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
        service.setProtocol(protocol);
        service.setInterface(Echo.class.getName());
        service.setRef(new EchoService());
        service.setMethods(Collections.singletonList(method));

        try {
            assertThatThrownBy(service::export)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index attribute and type attirbute not match")
                    .hasMessageContaining("index :0")
                    .hasMessageContaining("java.lang.Integer");
            assertThat(service.getExportedUrls()).isEmpty();
        } finally {
            service.unexport();
        }
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoService implements Echo {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }

    @Adaptive
    public static class StaticAdaptiveProtocol implements Protocol {
        @Override
        public int getDefaultPort() {
            throw new UnsupportedOperationException("getDefaultPort is not adaptive");
        }

        @Override
        public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
            return protocol(invoker.getUrl()).export(invoker);
        }

        @Override
        public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
            return protocol(url).refer(type, url);
        }

        @Override
        public void destroy() {
            throw new UnsupportedOperationException("destroy is not adaptive");
        }

        private Protocol protocol(URL url) {
            return ExtensionLoader.getExtensionLoader(Protocol.class)
                    .getExtension(url.getProtocol());
        }
    }

    @Adaptive
    public static class StaticAdaptiveCluster implements Cluster {
        @Override
        public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
            String name =
                    directory.getUrl()
                            .getParameter(Constants.CLUSTER_KEY, Constants.DEFAULT_CLUSTER);
            return ExtensionLoader.getExtensionLoader(Cluster.class)
                    .getExtension(name)
                    .join(directory);
        }
    }

    @Adaptive
    public static class StaticAdaptiveProxyFactory implements ProxyFactory {
        @Override
        public <T> T getProxy(Invoker<T> invoker) throws RpcException {
            return proxyFactory(invoker.getUrl()).getProxy(invoker);
        }

        @Override
        public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException {
            return proxyFactory(url).getInvoker(proxy, type, url);
        }

        private ProxyFactory proxyFactory(URL url) {
            String name = url.getParameter(Constants.PROXY_KEY, "javassist");
            return ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension(name);
        }
    }
}
