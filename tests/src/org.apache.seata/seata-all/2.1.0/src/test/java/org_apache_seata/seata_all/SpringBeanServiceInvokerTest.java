/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.apache.seata.saga.engine.exception.EngineExecutionException;
import org.apache.seata.saga.engine.invoker.impl.SpringBeanServiceInvoker;
import org.apache.seata.saga.statelang.domain.impl.AbstractTaskState.RetryImpl;
import org.apache.seata.saga.statelang.domain.impl.ServiceTaskStateImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticApplicationContext;

public class SpringBeanServiceInvokerTest {
    private static final String CONTEXT_ONLY_PARAMETER_CLASS = "yv66vgAAAEUADQoAAgADBwAEDAAFAAYBABBqYXZh"
            + "L2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAmb3JnL2V4YW1wbGUv"
            + "c2VhdGEvQ29udGV4dE9ubHlQYXJhbWV0ZXIBAARDb2RlAQAPTGluZU51bWJl"
            + "clRhYmxlAQAKU291cmNlRmlsZQEAGUNvbnRleHRPbmx5UGFyYW1ldGVyLmph"
            + "dmEAIQAHAAIAAAAAAAEAAQAFAAYAAQAJAAAAHQABAAEAAAAFKrcAAbEAAAAB"
            + "AAoAAAAGAAEAAAADAAEACwAAAAIADA==";

    @Test
    void invokesBeanMethodUsingConfiguredParameterTypeNames() throws Throwable {
        InvocationService service = new InvocationService();
        SpringBeanServiceInvoker invoker = invokerWithService(service);
        ServiceTaskStateImpl state = serviceTaskState("invocationService", "echo");
        state.setParameterTypes(List.of(String.class.getName()));

        Object result = invoker.invoke(state, "spring");

        assertThat(result).isEqualTo("echo:spring");
        assertThat(service.getEchoCount()).isEqualTo(1);
    }

    @Test
    void resolvesClassAvailableOnlyFromContextClassLoader(@TempDir Path tempDir) throws Exception {
        String contextOnlyParameterType = "org.example.seata.ContextOnlyParameter";
        Path classesDirectory = tempDir.resolve("context-loader-classes");
        Path classFile = classesDirectory.resolve("org/example/seata/ContextOnlyParameter.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getDecoder().decode(CONTEXT_ONLY_PARAMETER_CLASS));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader contextClassLoader = new URLClassLoader(
                new URL[] {classesDirectory.toUri().toURL()},
                null)) {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class<?> resolvedClass = new TestableSpringBeanServiceInvoker().resolveClassName(contextOnlyParameterType);

            assertThat(resolvedClass.getName()).isEqualTo(contextOnlyParameterType);
            assertThat(resolvedClass.getClassLoader()).isSameAs(contextClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void reportsUnknownConfiguredParameterTypeAfterTryingContextClassLoader() {
        SpringBeanServiceInvoker invoker = invokerWithService(new InvocationService());
        ServiceTaskStateImpl state = serviceTaskState("invocationService", "echo");
        state.setParameterTypes(List.of("org.example.seata.UnknownParameter"));

        EngineExecutionException exception = assertThrows(
                EngineExecutionException.class,
                () -> invoker.invoke(state, "spring"));

        assertThat(exception.getMessage()).contains("Parameter class not found");
    }

    @Test
    void retriesFailedInvocationWhenConfiguredExceptionClassMatches() throws Throwable {
        InvocationService service = new InvocationService();
        SpringBeanServiceInvoker invoker = invokerWithService(service);
        ServiceTaskStateImpl state = serviceTaskState("invocationService", "failOnceForSystemException");
        state.setRetry(List.of(retryFor(IllegalStateException.class.getName())));

        Object result = invoker.invoke(state);

        assertThat(result).isEqualTo("retried-system");
        assertThat(service.getSystemExceptionAttempts()).isEqualTo(2);
    }

    @Test
    void resolvesConfiguredRetryExceptionWithContextClassLoaderFallback() throws Throwable {
        InvocationService service = new InvocationService();
        SpringBeanServiceInvoker invoker = invokerWithService(service);
        ServiceTaskStateImpl state = serviceTaskState("invocationService", "failOnceForSystemException");
        String contextOnlyExceptionType = "org.example.seata.ContextOnlyRetryException";
        state.setRetry(List.of(retryFor(contextOnlyExceptionType)));
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(aliasingClassLoader(contextOnlyExceptionType, IllegalStateException.class));

        try {
            Object result = invoker.invoke(state);

            assertThat(result).isEqualTo("retried-system");
            assertThat(service.getSystemExceptionAttempts()).isEqualTo(2);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static SpringBeanServiceInvoker invokerWithService(InvocationService service) {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("invocationService", service);

        SpringBeanServiceInvoker invoker = new SpringBeanServiceInvoker();
        invoker.setApplicationContext(applicationContext);
        return invoker;
    }

    private static ServiceTaskStateImpl serviceTaskState(String serviceName, String serviceMethod) {
        ServiceTaskStateImpl state = new ServiceTaskStateImpl();
        state.setName(serviceMethod);
        state.setServiceName(serviceName);
        state.setServiceMethod(serviceMethod);
        return state;
    }

    private static RetryImpl retryFor(String exceptionClassName) {
        RetryImpl retry = new RetryImpl();
        retry.setExceptions(List.of(exceptionClassName));
        retry.setIntervalSeconds(0);
        retry.setMaxAttempts(1);
        retry.setBackoffRate(1);
        return retry;
    }

    private static ClassLoader aliasingClassLoader(String aliasedName, Class<?> targetClass) {
        return new ClassLoader(SpringBeanServiceInvokerTest.class.getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (aliasedName.equals(name)) {
                    return targetClass;
                }
                return super.loadClass(name);
            }
        };
    }

    public static class TestableSpringBeanServiceInvoker extends SpringBeanServiceInvoker {
        public Class<?> resolveClassName(String className) {
            return classForName(className);
        }
    }

    public static class InvocationService {
        private int echoCount;
        private int systemExceptionAttempts;

        public String echo(String value) {
            echoCount++;
            return "echo:" + value;
        }

        public String failOnceForSystemException() {
            systemExceptionAttempts++;
            if (systemExceptionAttempts == 1) {
                throw new IllegalStateException("retryable failure");
            }
            return "retried-system";
        }

        public int getEchoCount() {
            return echoCount;
        }

        public int getSystemExceptionAttempts() {
            return systemExceptionAttempts;
        }
    }
}
