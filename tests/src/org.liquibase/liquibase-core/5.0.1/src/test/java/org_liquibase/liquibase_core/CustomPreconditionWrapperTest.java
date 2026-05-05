/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.exception.PreconditionFailedException;
import liquibase.precondition.CustomPrecondition;
import liquibase.precondition.CustomPreconditionWrapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomPreconditionWrapperTest {

    @BeforeEach
    void resetCustomPreconditionState() {
        ExampleCustomPrecondition.checked = false;
        ExampleCustomPrecondition.configuredValue = null;
        NonCustomPrecondition.constructorInvocations = 0;
    }

    @Test
    void checkInstantiatesCustomPreconditionWithScopedClassLoaderAndAppliesParameters() throws Exception {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(ExampleCustomPrecondition.class.getName());
        wrapper.setParam("configuredValue", "expected-value");

        wrapper.check(null, null, null, null);

        assertThat(ExampleCustomPrecondition.checked).isTrue();
        assertThat(ExampleCustomPrecondition.configuredValue).isEqualTo("expected-value");
    }

    @Test
    void checkFallsBackToDefaultClassForNameAfterScopedClassCastFailure() throws Exception {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(ExampleCustomPrecondition.class.getName());
        DuplicateCustomPreconditionClassLoader classLoader = new DuplicateCustomPreconditionClassLoader(
                CustomPreconditionWrapperTest.class.getClassLoader()
        );

        try {
            Scope.child(Scope.Attr.classLoader, classLoader, () -> wrapper.check(null, null, null, null));
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            throw new TestAbortedException(
                    "Native image runtime does not support defining duplicate application classes from custom ClassLoader bytes",
                    error
            );
        }

        if (isNativeImageRuntime() && !classLoader.loadedDuplicatePrecondition) {
            throw new TestAbortedException(
                    "Native image runtime did not reload the custom precondition through the isolated ClassLoader"
            );
        }

        assertThat(classLoader.loadedDuplicatePrecondition).isTrue();
        assertThat(ExampleCustomPrecondition.checked).isTrue();
    }

    @Test
    void checkReportsFailureWhenDefaultClassForNameFallbackIsNotACustomPrecondition() {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(NonCustomPrecondition.class.getName());

        assertThatThrownBy(() -> wrapper.check(null, null, null, null))
                .isInstanceOf(PreconditionFailedException.class)
                .hasCauseInstanceOf(ClassCastException.class);
        assertThat(NonCustomPrecondition.constructorInvocations).isEqualTo(2);
    }

    private static final class DuplicateCustomPreconditionClassLoader extends ClassLoader {

        private final Set<String> duplicateClassNames = Set.of(
                CustomPrecondition.class.getName(),
                ExampleCustomPrecondition.class.getName()
        );

        private boolean loadedDuplicatePrecondition;

        private DuplicateCustomPreconditionClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (duplicateClassNames.contains(name)) {
                        loadedClass = defineClassFromParentResource(name);
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineClassFromParentResource(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(className);
                }
                byte[] classBytes = input.readAllBytes();
                Class<?> definedClass = defineClass(className, classBytes, 0, classBytes.length);
                if (ExampleCustomPrecondition.class.getName().equals(className)) {
                    loadedDuplicatePrecondition = true;
                }
                return definedClass;
            } catch (IOException exception) {
                throw new ClassNotFoundException(className, exception);
            }
        }
    }

    public static class NonCustomPrecondition {

        private static int constructorInvocations;

        public NonCustomPrecondition() {
            constructorInvocations++;
        }
    }

    public static class ExampleCustomPrecondition implements CustomPrecondition {

        private static boolean checked;
        private static String configuredValue;

        public ExampleCustomPrecondition() {
        }

        public void setConfiguredValue(String configuredValue) {
            ExampleCustomPrecondition.configuredValue = configuredValue;
        }

        @Override
        public void check(Database database)
                throws CustomPreconditionFailedException, CustomPreconditionErrorException {
            checked = true;
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
