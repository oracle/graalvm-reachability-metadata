/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.mockito.internal.creation.proxy.ProxyMockMaker;
import org.mockito.internal.creation.settings.CreationSettings;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationContainer;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodHandleProxyTest {
    public interface DefaultGreetingService {
        String prefix();

        default String greeting(String name) {
            return prefix() + ", " + name;
        }
    }

    @Test
    void proxyMockMakerCanInvokeDefaultInterfaceMethodThroughMethodHandles() {
        CreationSettings<DefaultGreetingService> settings = new CreationSettings<>();
        settings.setTypeToMock(DefaultGreetingService.class);
        DefaultMethodHandler handler = new DefaultMethodHandler(settings);

        DefaultGreetingService service =
                createMethodHandleProxyMockMaker().createMock(settings, handler);

        assertThat(service.greeting("GraalVM")).isEqualTo("Hello, GraalVM");
    }

    @SuppressWarnings("removal")
    private static ProxyMockMaker createMethodHandleProxyMockMaker() {
        SecurityManager originalSecurityManager = System.getSecurityManager();
        BlockingSecurityManager blockingSecurityManager = new BlockingSecurityManager();
        try {
            System.setSecurityManager(blockingSecurityManager);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return new ProxyMockMaker();
        }
        try {
            blockingSecurityManager.blockInvocationHandlerLookup();
            return new ProxyMockMaker();
        } finally {
            blockingSecurityManager.allowInvocationHandlerLookup();
            System.setSecurityManager(originalSecurityManager);
        }
    }

    @SuppressWarnings("removal")
    private static final class BlockingSecurityManager extends SecurityManager {
        private static final String INVOKE_DEFAULT_PROXY =
                "org.mockito.internal.creation.proxy.InvokeDefaultProxy";

        private boolean blockInvocationHandlerLookup;

        private void blockInvocationHandlerLookup() {
            blockInvocationHandlerLookup = true;
        }

        private void allowInvocationHandlerLookup() {
            blockInvocationHandlerLookup = false;
        }

        @Override
        public void checkPackageAccess(String packageName) {
            if (blockInvocationHandlerLookup
                    && "java.lang.reflect".equals(packageName)
                    && isInvokeDefaultProxyLookup()) {
                throw new SecurityException(packageName);
            }
        }

        @Override
        public void checkPermission(Permission permission) {
            // Allow unrelated checks while this manager only masks InvocationHandler lookup.
        }

        private static boolean isInvokeDefaultProxyLookup() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (INVOKE_DEFAULT_PROXY.equals(element.getClassName())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class DefaultMethodHandler implements MockHandler<DefaultGreetingService> {
        private final MockCreationSettings<DefaultGreetingService> settings;

        private DefaultMethodHandler(MockCreationSettings<DefaultGreetingService> settings) {
            this.settings = settings;
        }

        @Override
        public Object handle(Invocation invocation) throws Throwable {
            if ("prefix".equals(invocation.getMethod().getName())) {
                return "Hello";
            }
            return invocation.callRealMethod();
        }

        @Override
        public MockCreationSettings<DefaultGreetingService> getMockSettings() {
            return settings;
        }

        @Override
        public InvocationContainer getInvocationContainer() {
            return null;
        }
    }
}
