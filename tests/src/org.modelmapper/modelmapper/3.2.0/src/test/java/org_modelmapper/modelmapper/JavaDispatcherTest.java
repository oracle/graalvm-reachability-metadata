/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.security.Permission;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassReader;
import org.modelmapper.internal.asm.ClassWriter;
import org.modelmapper.internal.asm.Type;
import org.modelmapper.internal.asm.commons.ClassRemapper;
import org.modelmapper.internal.asm.commons.Remapper;
import org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher;

public class JavaDispatcherTest {
    private static final String SYSTEM_TYPE = "java/lang/System";
    private static final String PROXY_SYSTEM_TYPE = Type.getInternalName(ProxySystem.class);

    @Test
    void createsProxyWithDefaultDispatchersWhenProxiedTypeIsUnavailable() {
        MissingTypeDispatcher dispatcher = new ConfigurableJavaDispatcher<>(
            MissingTypeDispatcher.class,
            false).run();

        assertThat(dispatcher.text()).isNull();
        assertThat(dispatcher.number()).isZero();
    }

    @Test
    void inspectsProxyMethodsBeforeGeneratingDispatcherForUnavailableType() {
        try {
            MissingTypeDispatcher dispatcher = new ConfigurableJavaDispatcher<>(
                MissingTypeDispatcher.class,
                true).run();

            assertThat(dispatcher.text()).isNull();
            assertThat(dispatcher.number()).isZero();
        } catch (RuntimeException exception) {
            assertThat(exception).isNotInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void invokesSecurityManagerPermissionCheckInIsolatedDispatcher() throws Exception {
        ProxySystem.reset();
        ClassLoader classLoader = new SystemRedirectingClassLoader(getClass().getClassLoader());
        Class<?> runnerType = classLoader.loadClass(IsolatedRunner.class.getName());
        Callable<?> runner = (Callable<?>) runnerType.getConstructor().newInstance();

        assertThat(runner.call()).isEqualTo(Boolean.TRUE);
        assertThat(ProxySystem.checkedJavaDispatcherPermission()).isTrue();
    }

    @JavaDispatcher.Defaults
    @JavaDispatcher.Proxied("org.modelmapper.dynamicaccess.MissingDispatcherTarget")
    public interface MissingTypeDispatcher {
        String text();

        int number();
    }

    private static final class ConfigurableJavaDispatcher<T> extends JavaDispatcher<T> {
        private ConfigurableJavaDispatcher(Class<T> proxy, boolean generate) {
            super(proxy, JavaDispatcherTest.class.getClassLoader(), generate);
        }
    }

    public static final class IsolatedRunner implements Callable<Boolean> {
        @Override
        public Boolean call() {
            MissingTypeDispatcher dispatcher = JavaDispatcher.of(MissingTypeDispatcher.class).run();
            return dispatcher.number() == 0 && dispatcher.text() == null;
        }
    }

    @SuppressWarnings("removal")
    public static final class ProxySystem {
        private static final RecordingSecurityManager SECURITY_MANAGER = new RecordingSecurityManager();

        private ProxySystem() {
        }

        public static SecurityManager getSecurityManager() {
            return SECURITY_MANAGER;
        }

        public static String getProperty(String key, String defaultValue) {
            return System.getProperty(key, defaultValue);
        }

        private static void reset() {
            SECURITY_MANAGER.checkedJavaDispatcherPermission = false;
        }

        private static boolean checkedJavaDispatcherPermission() {
            return SECURITY_MANAGER.checkedJavaDispatcherPermission;
        }
    }

    private static final class SystemRedirectingClassLoader extends ClassLoader {
        private SystemRedirectingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && isChildFirst(name)) {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        type = super.loadClass(name, false);
                    }
                } else if (type == null) {
                    type = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] binaryRepresentation = inputStream.readAllBytes();
                if (JavaDispatcher.class.getName().equals(name)) {
                    binaryRepresentation = redirectSystemReferences(binaryRepresentation);
                }
                return defineClass(name, binaryRepresentation, 0, binaryRepresentation.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private static boolean isChildFirst(String name) {
            return name.equals(IsolatedRunner.class.getName())
                || name.equals(MissingTypeDispatcher.class.getName())
                || name.equals(JavaDispatcher.class.getName())
                || name.startsWith(JavaDispatcher.class.getName() + "$");
        }

        private static byte[] redirectSystemReferences(byte[] binaryRepresentation) {
            ClassReader reader = new ClassReader(binaryRepresentation);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassRemapper(writer, new SystemReferenceRemapper()), 0);
            return writer.toByteArray();
        }
    }

    private static final class SystemReferenceRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return SYSTEM_TYPE.equals(internalName) ? PROXY_SYSTEM_TYPE : internalName;
        }
    }

    @SuppressWarnings("removal")
    private static final class RecordingSecurityManager extends SecurityManager {
        private boolean checkedJavaDispatcherPermission;

        @Override
        public void checkPermission(Permission permission) {
            if ("org.modelmapper.internal.bytebuddy.createJavaDispatcher".equals(permission.getName())) {
                checkedJavaDispatcherPermission = true;
            }
        }
    }
}
