/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jboss.interceptor.proxy.InterceptorException;
import org.jboss.interceptor.proxy.InterceptorProxyCreatorImpl;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterceptorProxyCreatorImplTest {

    @Test
    void createsProxyInstanceUsingPublicNoArgConstructor() {
        final InterceptorProxyCreatorImpl proxyCreator = new InterceptorProxyCreatorImpl(null, null, null);

        final InterceptorException proxy = proxyCreator.createProxyInstance(InterceptorException.class, null);

        assertThat(proxy).isNotNull();
    }

    @Test
    void createsAdvisedSubclassInstanceUsingGeneratedSubclassConstructor() {
        try {
            final InterceptorProxyCreatorImpl proxyCreator = new InterceptorProxyCreatorImpl(null, null, null);

            final InterceptorException proxy = proxyCreator.createAdvisedSubclassInstance(
                    ReflectiveClassMetadata.of(InterceptorException.class),
                    new Class<?>[] {},
                    new Object[] {});

            assertThat(proxy).isInstanceOf(InterceptorException.class);
        } catch (InterceptorException exception) {
            rethrowIfNotNativeImageDynamicClassLoadingFailure(exception);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingFailure(error);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingFailure(InterceptorException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            cause = cause.getCause();
        }
        throw exception;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingFailure(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
