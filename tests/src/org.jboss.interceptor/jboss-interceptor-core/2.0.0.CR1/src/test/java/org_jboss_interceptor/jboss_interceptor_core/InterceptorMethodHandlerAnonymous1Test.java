/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javax.interceptor.InvocationContext;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.InterceptorMethodHandler;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.context.InterceptionChain;
import org.jboss.interceptor.spi.context.InvocationContextFactory;
import org.jboss.interceptor.spi.instance.InterceptorInstantiator;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.model.InterceptionModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterceptorMethodHandlerAnonymous1Test {

    @Test
    void restoredDefaultMethodHandlerInvokesProceedMethodOnDeserializedTargetProxy() throws Throwable {
        final ProxyBackedTarget targetProxy = new ProxyBackedTarget();
        final ClassMetadata<ProxyBackedTarget> targetMetadata = ReflectiveClassMetadata.of(ProxyBackedTarget.class);
        final InterceptorMethodHandler interceptorMethodHandler = new InterceptorMethodHandler(
                targetProxy,
                targetMetadata,
                emptyInterceptionModel(targetMetadata),
                noInterceptorInstantiator(),
                noInvocationContextRequired());

        final Object[] restoredGraph = deserialize(serialize(new Object[] {interceptorMethodHandler, targetProxy}));
        final ProxyBackedTarget restoredTargetProxy = (ProxyBackedTarget) restoredGraph[1];
        final Method proceedMethod = ProxyBackedTarget.class.getMethod("greeting", String.class);

        final Object result = restoredTargetProxy.getHandler().invoke(
                restoredTargetProxy,
                proceedMethod,
                proceedMethod,
                new Object[] {"coverage"});

        assertThat(result).isEqualTo("hello coverage");
    }

    private static InterceptionModel<ClassMetadata<?>, ?> emptyInterceptionModel(ClassMetadata<?> targetMetadata) {
        return InterceptionModelBuilder
                .<ClassMetadata<?>, Object>newBuilderFor(targetMetadata, Object.class)
                .build();
    }

    private static InterceptorInstantiator<Object, Object> noInterceptorInstantiator() {
        return interceptorReference -> {
            throw new AssertionError("No interceptor instances are required for this empty interception model");
        };
    }

    private static InvocationContextFactory noInvocationContextRequired() {
        return new NullInvocationContextFactory();
    }

    private static byte[] serialize(Object value) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object[] deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Object[]) stream.readObject();
        }
    }

    public static class NullInvocationContextFactory implements InvocationContextFactory {

        private static final long serialVersionUID = 1L;

        @Override
        public InvocationContext newInvocationContext(
                InterceptionChain chain,
                Object target,
                Method method,
                Object[] args) {
            return null;
        }

        @Override
        public InvocationContext newInvocationContext(
                InterceptionChain chain,
                Object target,
                Method method,
                Object timer) {
            return null;
        }
    }

    public static class ProxyBackedTarget implements ProxyObject, Serializable {

        private static final long serialVersionUID = 1L;

        private transient MethodHandler handler;

        @Override
        public void setHandler(MethodHandler handler) {
            this.handler = handler;
        }

        @Override
        public MethodHandler getHandler() {
            return handler;
        }

        public String greeting(String name) {
            return "hello " + name;
        }
    }
}
