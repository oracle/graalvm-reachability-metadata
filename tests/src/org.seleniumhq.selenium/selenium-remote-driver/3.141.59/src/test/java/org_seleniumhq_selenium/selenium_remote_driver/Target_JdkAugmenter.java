/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.RemoteExecuteMethod;
import org.openqa.selenium.remote.RemoteWebDriver;

@TargetClass(className = "org.openqa.selenium.remote.JdkAugmenter")
final class Target_JdkAugmenter {

    @Substitute
    @SuppressWarnings("unchecked")
    protected <X> X create(RemoteWebDriver driver, Map<String, AugmenterProvider> augmenters, X original) {
        Capabilities capabilities = driver.getCapabilities();
        Map<String, Object> capabilityMap = capabilities.asMap();
        Map<Method, InterfaceImplementation> handlers = new HashMap<>();
        Set<Class<?>> interfaces = new LinkedHashSet<>();

        Class<?> currentType = original.getClass();
        while (currentType != null) {
            interfaces.addAll(Arrays.asList(currentType.getInterfaces()));
            currentType = currentType.getSuperclass();
        }

        for (Map.Entry<String, Object> capabilityEntry : capabilityMap.entrySet()) {
            AugmenterProvider provider = augmenters.get(capabilityEntry.getKey());
            if (provider == null) {
                continue;
            }

            Object capabilityValue = capabilityEntry.getValue();
            if (capabilityValue instanceof Boolean enabled && !enabled) {
                continue;
            }

            Class<?> describedInterface = provider.getDescribedInterface();
            Preconditions.checkState(
                describedInterface.isInterface(),
                "JdkAugmenter can only augment interfaces. %s is not an interface.",
                describedInterface
            );
            interfaces.add(describedInterface);

            InterfaceImplementation implementation = provider.getImplementation(capabilityValue);
            Method[] methods = describedInterface.getMethods();
            for (Method method : methods) {
                InterfaceImplementation previous = handlers.put(method, implementation);
                Preconditions.checkState(
                    previous == null,
                    "Both %s and %s attempt to define %s.",
                    previous,
                    implementation.getClass(),
                    method.getName()
                );
            }
        }

        if (handlers.isEmpty()) {
            return original;
        }

        InvocationHandler invocationHandler = new StableJdkInvocationHandler<>(driver, original, handlers);
        Class<?>[] proxyInterfaces = interfaces.toArray(new Class<?>[0]);
        Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), proxyInterfaces, invocationHandler);
        return (X) proxy;
    }

    private static final class StableJdkInvocationHandler<X> implements InvocationHandler {
        private final RemoteWebDriver driver;
        private final X realInstance;
        private final Map<Method, InterfaceImplementation> handlers;

        private StableJdkInvocationHandler(RemoteWebDriver driver, X realInstance, Map<Method, InterfaceImplementation> handlers) {
            this.driver = Objects.requireNonNull(driver);
            this.realInstance = Objects.requireNonNull(realInstance);
            this.handlers = Objects.requireNonNull(handlers);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            InterfaceImplementation handler = handlers.get(method);
            try {
                if (handler == null) {
                    return method.invoke(realInstance, args);
                }
                ExecuteMethod executeMethod = new RemoteExecuteMethod(driver);
                return handler.invoke(executeMethod, proxy, method, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
