/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.util.IdleConnectionHandler;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class IdleConnectionHandlerTest {
    private static final String IDLE_CONNECTION_HANDLER_CLASS_NAME =
            "org.apache.commons.httpclient.util.IdleConnectionHandler";

    @Test
    void legacyClassLiteralHelperLoadsIdleConnectionHandlerType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                IdleConnectionHandler.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                IdleConnectionHandler.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(IDLE_CONNECTION_HANDLER_CLASS_NAME);
        Class<?> resolvedJdkClass = (Class<?>) classLookup.invoke("java.lang.String");

        assertThat(resolvedClass).isSameAs(IdleConnectionHandler.class);
        assertThat(resolvedJdkClass).isSameAs(String.class);
    }

    @Test
    void legacyClassLiteralHelperWrapsMissingClassFailures() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                IdleConnectionHandler.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                IdleConnectionHandler.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        assertThatThrownBy(() -> classLookup.invoke("example.missing.IdleConnectionHandler"))
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessageContaining("example.missing.IdleConnectionHandler");
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (IdleConnectionHandlerClassLoader classLoader = newIdleConnectionHandlerClassLoader()) {
            Class<?> handlerClass = Class.forName(
                    IDLE_CONNECTION_HANDLER_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(handlerClass.getName()).isEqualTo(IDLE_CONNECTION_HANDLER_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(handlerClass).isSameAs(IdleConnectionHandler.class);
            } else {
                assertThat(handlerClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void closesTrackedConnectionsAndLeavesRemovedConnectionsOpen() {
        IdleConnectionHandler handler = new IdleConnectionHandler();
        CountingHttpConnection removedConnection = new CountingHttpConnection();
        CountingHttpConnection idleConnection = new CountingHttpConnection();
        CountingHttpConnection clearedConnection = new CountingHttpConnection();

        handler.add(removedConnection);
        handler.remove(removedConnection);
        handler.closeIdleConnections(0);

        assertThat(removedConnection.isClosed()).isFalse();

        handler.add(idleConnection);
        handler.closeIdleConnections(0);

        assertThat(idleConnection.isClosed()).isTrue();

        handler.add(clearedConnection);
        handler.removeAll();
        handler.closeIdleConnections(0);

        assertThat(clearedConnection.isClosed()).isFalse();
    }

    private static IdleConnectionHandlerClassLoader newIdleConnectionHandlerClassLoader() {
        URL location = IdleConnectionHandler.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new IdleConnectionHandlerClassLoader(new URL[] {location},
                IdleConnectionHandlerTest.class.getClassLoader());
    }

    private static final class IdleConnectionHandlerClassLoader extends URLClassLoader {
        private IdleConnectionHandlerClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (IDLE_CONNECTION_HANDLER_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }

    private static final class CountingHttpConnection extends HttpConnection {
        private boolean closed;

        private CountingHttpConnection() {
            super("example.invalid", 80);
        }

        @Override
        public void close() {
            this.closed = true;
            super.close();
        }

        private boolean isClosed() {
            return this.closed;
        }
    }
}
