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
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.spring.AnnotationBean;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.service.GenericException;
import com.alibaba.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;

public class AnnotationBeanTest {
    private static boolean directExtensionsInstalled;

    @Test
    void scansConfiguredPackagesForDubboServices() {
        AnnotationBean annotations = new AnnotationBean();
        annotations.setPackage("com_alibaba.dubbo.no_services");
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        annotations.postProcessBeanFactory(beanFactory);
        annotations.setPackage("com_alibaba.dubbo");
        PlainBean bean = new PlainBean();

        assertThat(annotations.postProcessBeforeInitialization(bean, "plainBean")).isSameAs(bean);
        assertThat(beanFactory.getBeanDefinitionNames()).hasSize(2);
        assertThat(annotations.getPackage()).isEqualTo("com_alibaba.dubbo");
    }

    @Test
    void injectsAGenericReferenceIntoAnAnnotatedField() throws Exception {
        installDirectExtensions();
        ApplicationConfig application = new ApplicationConfig("annotation-bean-test");
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("application", application);
        context.refresh();
        AnnotationBean annotations = new AnnotationBean();
        annotations.setApplicationContext(context);
        ClientBean client = new ClientBean();
        try {
            annotations.postProcessBeforeInitialization(client, "client");

            Object reply =
                    client.service.$invoke(
                            "echo", new String[] {String.class.getName()}, new Object[] {"field"});
            assertThat(reply).isEqualTo("echo:field");
        } finally {
            annotations.destroy();
            context.close();
        }
    }

    private static synchronized void installDirectExtensions() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
        if (!directExtensionsInstalled) {
            ExtensionLoader.getExtensionLoader(Protocol.class)
                    .addExtension("annotation-test", DirectProtocol.class);
            ExtensionLoader.getExtensionLoader(ProxyFactory.class)
                    .addExtension("annotation-test", DirectProxyFactory.class);
            directExtensionsInstalled = true;
        }
    }

    public static class ClientBean {
        @Reference(
                interfaceClass = GenericService.class,
                generic = true,
                url = "annotation-test://127.0.0.1",
                proxy = "annotation-test")
        private GenericService service;
    }

    public static class DirectProxyFactory implements ProxyFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getProxy(Invoker<T> invoker) throws RpcException {
            return (T) new GenericClient((Invoker<GenericService>) invoker);
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

    public static class GenericClient implements GenericService {
        private final Invoker<GenericService> invoker;

        GenericClient(Invoker<GenericService> invoker) {
            this.invoker = invoker;
        }

        @Override
        public Object $invoke(String method, String[] parameterTypes, Object[] args)
                throws GenericException {
            Result result =
                    invoker.invoke(
                            new RpcInvocation(
                                    "$invoke",
                                    new Class<?>[] {String.class, String[].class, Object[].class},
                                    new Object[] {method, parameterTypes, args}));
            return result.getValue();
        }
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
            Object[] arguments = (Object[]) invocation.getArguments()[2];
            return new RpcResult("echo:" + arguments[0]);
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

    public static class PlainBean {
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
