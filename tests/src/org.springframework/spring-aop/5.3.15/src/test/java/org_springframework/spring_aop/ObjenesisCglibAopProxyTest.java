/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.DefaultAopProxyFactory;
import org.springframework.objenesis.SpringObjenesis;

public class ObjenesisCglibAopProxyTest {
    @BeforeAll
    static void prepareProxyCreationModes() {
        System.setProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, "true");
        recordJdkProxyMetadataForNativeImageFallback();
    }

    @AfterAll
    static void restoreObjenesisProperty() {
        System.clearProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME);
    }

    @Test
    void cglibProxyFallsBackToDefaultConstructorWhenObjenesisIsIgnored() {
        GreetingService proxy = createProxy(DefaultGreetingService.class, new DefaultGreetingService());

        assertThat(proxy.greet("Spring")).isEqualTo("Hello Spring");
    }

    @Test
    void cglibProxyFallsBackToMatchingConstructorWhenConstructorArgumentsAreConfigured() {
        AopProxy aopProxy = createAopProxy(ConstructorGreetingService.class,
                new ConstructorGreetingService("Hello"));
        configureConstructorArgumentsIfCglibProxy(aopProxy, new Object[] {"Proxy"}, new Class<?>[] {String.class});

        GreetingService proxy = (GreetingService) aopProxy.getProxy();

        assertThat(proxy.greet("Spring")).isEqualTo("Hello Spring");
    }

    private static GreetingService createProxy(Class<? extends GreetingService> targetClass, GreetingService target) {
        return (GreetingService) createAopProxy(targetClass, target).getProxy();
    }

    private static AopProxy createAopProxy(Class<? extends GreetingService> targetClass, GreetingService target) {
        AdvisedSupport config = new AdvisedSupport(GreetingService.class);
        config.setTargetClass(targetClass);
        config.setTarget(target);
        config.setProxyTargetClass(true);
        return new DefaultAopProxyFactory().createAopProxy(config);
    }

    private static void recordJdkProxyMetadataForNativeImageFallback() {
        AdvisedSupport config = new AdvisedSupport(GreetingService.class);
        config.setTarget(new DefaultGreetingService());
        GreetingService proxy = (GreetingService) new DefaultAopProxyFactory().createAopProxy(config).getProxy();

        assertThat(proxy.greet("Spring")).isEqualTo("Hello Spring");
    }

    private static void configureConstructorArgumentsIfCglibProxy(
            AopProxy aopProxy, Object[] constructorArgs, Class<?>[] constructorArgTypes) {
        Class<?> proxyClass = aopProxy.getClass();
        if (!proxyClass.getName().endsWith("ObjenesisCglibAopProxy")) {
            return;
        }
        try {
            Method method = findSetConstructorArgumentsMethod(proxyClass);
            method.setAccessible(true);
            method.invoke(aopProxy, constructorArgs, constructorArgTypes);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to configure CGLIB proxy constructor arguments", ex);
        }
    }

    private static Method findSetConstructorArgumentsMethod(Class<?> proxyClass) throws NoSuchMethodException {
        Class<?> currentClass = proxyClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod("setConstructorArguments", Object[].class, Class[].class);
            } catch (NoSuchMethodException ex) {
                currentClass = currentClass.getSuperclass();
            }
        }
        throw new NoSuchMethodException("setConstructorArguments");
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class DefaultGreetingService implements GreetingService {
        public DefaultGreetingService() {
        }

        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class ConstructorGreetingService implements GreetingService {
        private final String greeting;

        public ConstructorGreetingService(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public String greet(String name) {
            return this.greeting + " " + name;
        }
    }
}
