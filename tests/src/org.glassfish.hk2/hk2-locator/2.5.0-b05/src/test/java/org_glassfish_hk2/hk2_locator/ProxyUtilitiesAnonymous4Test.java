/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_locator;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.util.proxy.ProxyObject;

import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.ProxyCtl;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.internal.ConstantActiveDescriptor;
import org.jvnet.hk2.internal.ProxyUtilities;
import org.jvnet.hk2.internal.ServiceLocatorImpl;

public class ProxyUtilitiesAnonymous4Test {
    @Test
    void generateProxyForConcreteClassCreatesJavassistProxyInstance() {
        final ServiceLocatorImpl locator = new ServiceLocatorImpl("proxy-utilities-anonymous-4-test", null);
        try {
            final ConstantActiveDescriptor<ConcreteService> descriptor =
                    new ConstantActiveDescriptor<>(new ConcreteService(), locator);

            final ConcreteService proxy;
            try {
                proxy = generateConcreteProxy(locator, descriptor);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
                return;
            } catch (MultiException exception) {
                if (!hasUnsupportedFeatureError(exception)) {
                    throw exception;
                }
                return;
            }

            assertThat(proxy).isNotNull();
            assertThat(proxy).isInstanceOf(ConcreteService.class);
            assertThat(proxy.getClass()).isNotEqualTo(ConcreteService.class);
            assertThat(proxy).isInstanceOf(ProxyObject.class);
            assertThat(proxy).isInstanceOf(ProxyCtl.class);
            assertThat(proxy.getClass().getClassLoader().toString()).contains("DelegatingClassLoader");
        } finally {
            locator.shutdown();
        }
    }

    private static ConcreteService generateConcreteProxy(
            ServiceLocatorImpl locator,
            ConstantActiveDescriptor<ConcreteService> descriptor) {
        return new ProxyUtilities().generateProxy(
                ConcreteService.class,
                locator,
                descriptor,
                null,
                null);
    }

    private static boolean hasUnsupportedFeatureError(MultiException exception) {
        for (Throwable error : exception.getErrors()) {
            if (hasUnsupportedFeatureError(error)) {
                return true;
            }
        }

        return hasUnsupportedFeatureError(exception.getCause());
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    public static class ConcreteService {
    }
}
