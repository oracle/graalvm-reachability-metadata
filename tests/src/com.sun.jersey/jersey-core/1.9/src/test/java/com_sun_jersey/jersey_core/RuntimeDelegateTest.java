/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RuntimeDelegateTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.class.getName();
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/" + RUNTIME_DELEGATE_PROPERTY;

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

    @BeforeEach
    void clearRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
        System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
    }

    @AfterEach
    void restoreRuntimeDelegateState() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    public void reportsClassLoaderLocationsWhenProviderIsNotRuntimeDelegate() throws Exception {
        runWithContextClassLoader(new InvalidProviderClassLoader(RuntimeDelegateTest.class.getClassLoader()), () ->
                assertThatThrownBy(RuntimeDelegate::getInstance)
                        .isInstanceOf(LinkageError.class)
                        .hasMessageContaining("ClassCastException: attempting to cast")
                        .hasMessageContaining("javax/ws/rs/ext/RuntimeDelegate.class"));
    }

    private static void runWithContextClassLoader(ClassLoader contextClassLoader, ThrowingRunnable action)
            throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread("runtime-delegate-context-loader") {
            @Override
            public ClassLoader getContextClassLoader() {
                return contextClassLoader;
            }

            @Override
            public void run() {
                try {
                    action.run();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }
        };

        worker.start();
        worker.join(10_000L);
        if (worker.isAlive()) {
            worker.interrupt();
        }
        assertThat(worker.isAlive()).isFalse();

        Throwable throwable = failure.get();
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable != null) {
            throw new AssertionError(throwable);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class InvalidRuntimeDelegateProvider {
        public InvalidRuntimeDelegateProvider() {
        }
    }

    private static final class InvalidProviderClassLoader extends ClassLoader {
        private InvalidProviderClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RUNTIME_DELEGATE_SERVICE.equals(name)) {
                byte[] providerName = InvalidRuntimeDelegateProvider.class.getName()
                        .getBytes(StandardCharsets.UTF_8);
                return new ByteArrayInputStream(providerName);
            }
            return super.getResourceAsStream(name);
        }
    }
}
