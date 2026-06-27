/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.Installer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class InstallerTest {
    @AfterEach
    void resetInstaller() {
        Installer.premain(null, null);
    }

    @Test
    @SuppressWarnings("removal")
    void getInstrumentationChecksPermissionWithSecurityManager() {
        Instrumentation instrumentation = new RecordingInstrumentation();
        RecordingSecurityManager securityManager = new RecordingSecurityManager();
        SecurityManager originalSecurityManager = System.getSecurityManager();
        Installer.premain(null, instrumentation);

        Instrumentation returnedInstrumentation;
        List<String> checkedPermissionNames;
        try {
            System.setSecurityManager(securityManager);
        } catch (UnsupportedOperationException ignored) {
            assertThat(Installer.getInstrumentation()).isSameAs(instrumentation);
            return;
        }
        try {
            returnedInstrumentation = Installer.getInstrumentation();
            checkedPermissionNames = new ArrayList<>(securityManager.checkedPermissionNames);
        } finally {
            System.setSecurityManager(originalSecurityManager);
        }

        assertThat(returnedInstrumentation).isSameAs(instrumentation);
        assertThat(checkedPermissionNames).contains("net.bytebuddy.agent.getInstrumentation");
    }

    @SuppressWarnings("removal")
    private static final class RecordingSecurityManager extends SecurityManager {
        private final List<String> checkedPermissionNames = new ArrayList<>();

        @Override
        public void checkPermission(Permission permission) {
            checkedPermissionNames.add(permission.getName());
        }
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return false;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
        }

        @Override
        public boolean isModifiableClass(Class<?> type) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object object) {
            return 0L;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        }

        @Override
        public void redefineModule(Module module,
                                   Set<Module> extraReads,
                                   Map<String, Set<Module>> extraExports,
                                   Map<String, Set<Module>> extraOpens,
                                   Set<Class<?>> extraUses,
                                   Map<Class<?>, List<Class<?>>> extraProvides) {
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
