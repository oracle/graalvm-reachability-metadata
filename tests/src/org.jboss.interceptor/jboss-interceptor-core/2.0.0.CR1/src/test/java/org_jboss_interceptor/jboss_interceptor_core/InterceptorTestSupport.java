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

import javax.ejb.PrePassivate;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.proxy.InterceptorMethodHandler;
import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.InterceptorMetadataUtils;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorMetadata;

final class InterceptorTestSupport {
    private InterceptorTestSupport() {
    }

    static InterceptorMetadata<?> interceptorMetadata(Class<?> type) {
        return InterceptorMetadataUtils.readMetadataForInterceptorClass(
                ClassMetadataInterceptorReference.of(ReflectiveClassMetadata.of(type)));
    }

    static InterceptorMethodHandler handler(Object target, Class<?> type) throws NoSuchMethodException {
        ClassMetadata<?> metadata = ReflectiveClassMetadata.of(type);
        InterceptionModelBuilder<ClassMetadata<?>, ?> builder = InterceptionModelBuilder.newBuilderFor(metadata);
        builder.interceptAroundInvoke(GreetingService.class.getMethod("greet", String.class))
                .with(interceptorMetadata(DecoratingInterceptor.class));
        return new InterceptorMethodHandler(target, metadata, builder.build(),
                new DirectClassInterceptorInstantiator(), new DefaultInvocationContextFactory());
    }

    static <T> T roundTrip(T value, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return type.cast(input.readObject());
        }
    }

    public static class GreetingService implements Serializable {
        public String greet(String name) throws Throwable {
            return "Hello, " + name;
        }

        public String description() {
            return "greeting service";
        }

        @Override
        public String toString() {
            return description();
        }
    }

    public static class DecoratingInterceptor implements Serializable {
        @AroundInvoke
        public Object decorate(InvocationContext context) throws Exception {
            return "[" + context.proceed() + "]";
        }
    }

    public static class LifecycleService implements Serializable {
        private int passivations;

        @PrePassivate
        public void passivate() {
            passivations++;
        }

        public int passivations() {
            return passivations;
        }
    }

    /// A statically declared adapter for the public method-handler SPI.
    public static class GreetingProxy extends GreetingService implements ProxyObject {
        private transient MethodHandler handler;

        @Override
        public void setHandler(MethodHandler methodHandler) {
            handler = methodHandler;
        }

        @Override
        public MethodHandler getHandler() {
            return handler;
        }

        @Override
        public String greet(String name) throws Throwable {
            Method method = GreetingService.class.getMethod("greet", String.class);
            Method proceed = GreetingProxy.class.getMethod("greetDirect", String.class);
            return (String) handler.invoke(this, method, proceed, new Object[]{name});
        }

        public String greetDirect(String name) throws Throwable {
            return super.greet(name);
        }
    }
}
