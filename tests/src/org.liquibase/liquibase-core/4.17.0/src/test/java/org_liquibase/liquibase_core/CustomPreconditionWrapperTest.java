/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;
import liquibase.precondition.CustomPreconditionWrapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomPreconditionWrapperTest {

    @BeforeEach
    void clearRecordingPreconditionState() {
        RecordingCustomPrecondition.reset();
    }

    @Test
    void checkInstantiatesCustomPreconditionFromScopeClassLoaderAndAppliesParameters() throws Exception {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(RecordingCustomPrecondition.class.getName());
        wrapper.setParam("expectedValue", "configured-value");

        wrapper.check(new H2Database(), new DatabaseChangeLog(), null, null);

        assertThat(RecordingCustomPrecondition.instancesCreated()).isEqualTo(1);
        assertThat(RecordingCustomPrecondition.checkedDatabases()).containsExactly(H2Database.class.getName());
        assertThat(RecordingCustomPrecondition.checkedValues()).containsExactly("configured-value");
    }

    @Test
    void checkFallsBackToDefaultClassLoadingWhenScopeClassLoaderFindsIncompatibleType() throws Exception {
        try {
            ClassLoader incompatibleLoader = new IncompatibleCustomPreconditionClassLoader(
                    CustomPreconditionWrapperTest.class.getClassLoader()
            );
            Scope.child(Map.of(Scope.Attr.classLoader.name(), incompatibleLoader), () -> {
                CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
                wrapper.setClassName(RecordingCustomPrecondition.class.getName());
                wrapper.setParam("expectedValue", "fallback-value");

                wrapper.check(new H2Database(), new DatabaseChangeLog(), null, null);
            });

            assertThat(RecordingCustomPrecondition.instancesCreated()).isEqualTo(1);
            assertThat(RecordingCustomPrecondition.checkedDatabases()).containsExactly(H2Database.class.getName());
            assertThat(RecordingCustomPrecondition.checkedValues()).containsExactly("fallback-value");
        } catch (Error error) {
            throwIfNotUnsupportedDynamicClassLoading(error);
        }
    }

    private static void throwIfNotUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static final class RecordingCustomPrecondition implements CustomPrecondition {
        private static int instancesCreated;
        private static final Set<String> CHECKED_DATABASES = new LinkedHashSet<>();
        private static final Set<String> CHECKED_VALUES = new LinkedHashSet<>();

        private String expectedValue;

        public RecordingCustomPrecondition() {
            instancesCreated++;
        }

        public void setExpectedValue(String expectedValue) {
            this.expectedValue = expectedValue;
        }

        @Override
        public void check(Database database)
                throws CustomPreconditionFailedException, CustomPreconditionErrorException {
            CHECKED_DATABASES.add(database.getClass().getName());
            CHECKED_VALUES.add(expectedValue);
        }

        static void reset() {
            instancesCreated = 0;
            CHECKED_DATABASES.clear();
            CHECKED_VALUES.clear();
        }

        static int instancesCreated() {
            return instancesCreated;
        }

        static Set<String> checkedDatabases() {
            return CHECKED_DATABASES;
        }

        static Set<String> checkedValues() {
            return CHECKED_VALUES;
        }
    }

    private static final class IncompatibleCustomPreconditionClassLoader extends ClassLoader {
        private static final Set<String> ISOLATED_CLASSES = Set.of(
                RecordingCustomPrecondition.class.getName(),
                CustomPrecondition.class.getName()
        );

        private IncompatibleCustomPreconditionClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!ISOLATED_CLASSES.contains(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    byte[] classBytes = readClassBytes(name);
                    loadedClass = defineClass(name, classBytes, 0, classBytes.length);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static byte[] readClassBytes(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            ClassLoader loader = CustomPreconditionWrapperTest.class.getClassLoader();
            try (InputStream inputStream = loader.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(className);
                }
                return inputStream.readAllBytes();
            } catch (IOException exception) {
                ClassNotFoundException classNotFoundException = new ClassNotFoundException(className);
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }
    }
}
