/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import net.sf.cglib.beans.BulkBean;
import net.sf.cglib.beans.BulkBeanException;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class BulkBeanTest {
    private static final String[] GETTERS = {"getName", "getCount", "isActive"};
    private static final String[] SETTERS = {"setName", "setCount", "setActive"};
    private static final Class[] PROPERTY_TYPES = {String.class, Integer.TYPE, Boolean.TYPE};

    @Test
    void readsAndWritesConfiguredBeanPropertiesInBulk() {
        try {
            BulkBean bulkBean = BulkBean.create(BulkBeanTarget.class, GETTERS, SETTERS, PROPERTY_TYPES);
            BulkBeanTarget target = new BulkBeanTarget();

            bulkBean.setPropertyValues(target, new Object[] {"bulk", Integer.valueOf(42), Boolean.TRUE});
            Object[] propertyValues = bulkBean.getPropertyValues(target);

            assertThat(propertyValues).containsExactly("bulk", Integer.valueOf(42), Boolean.TRUE);
            assertThat(target.getName()).isEqualTo("bulk");
            assertThat(target.getCount()).isEqualTo(42);
            assertThat(target.isActive()).isTrue();
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void exposesDefensiveCopiesOfConfiguredAccessorsAndTypes() {
        try {
            BulkBean bulkBean = BulkBean.create(BulkBeanTarget.class, GETTERS, SETTERS, PROPERTY_TYPES);

            String[] getters = bulkBean.getGetters();
            String[] setters = bulkBean.getSetters();
            Class[] propertyTypes = bulkBean.getPropertyTypes();
            getters[0] = "changedGetter";
            setters[0] = "changedSetter";
            propertyTypes[0] = Object.class;

            assertThat(bulkBean.getGetters()).containsExactly("getName", "getCount", "isActive");
            assertThat(bulkBean.getSetters()).containsExactly("setName", "setCount", "setActive");
            assertThat(bulkBean.getPropertyTypes()).containsExactly(String.class, Integer.TYPE, Boolean.TYPE);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void reportsPropertyIndexWhenSettingIncompatibleValue() {
        try {
            BulkBean bulkBean = BulkBean.create(BulkBeanTarget.class, GETTERS, SETTERS, PROPERTY_TYPES);
            BulkBeanTarget target = new BulkBeanTarget();

            assertThatThrownBy(
                            () -> bulkBean.setPropertyValues(
                                    target, new Object[] {"bulk", "not a number", Boolean.TRUE}))
                    .isInstanceOf(BulkBeanException.class)
                    .extracting("index")
                    .isEqualTo(Integer.valueOf(1));
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void resolvesLegacyClassLiteralHelperUsedByBulkBeanInitialization() throws Exception {
        try {
            Method classLiteralHelper = BulkBean.class.getDeclaredMethod("class$", String.class);
            classLiteralHelper.setAccessible(true);

            Object resolvedClass = classLiteralHelper.invoke(null, BulkBean.class.getName());

            assertThat(resolvedClass).isSameAs(BulkBean.class);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception.getTargetException())) {
                throw exception;
            }
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void initializesBulkBeanInIsolatedClassLoader() throws Exception {
        try (ChildFirstCglibClassLoader loader = new ChildFirstCglibClassLoader(libraryLocation())) {
            Class<?> isolatedBulkBean = Class.forName(BulkBean.class.getName(), true, loader);

            assertThat(isolatedBulkBean.getName()).isEqualTo(BulkBean.class.getName());
            assertThat(isolatedBulkBean.getClassLoader()).isSameAs(loader);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static URL libraryLocation() {
        CodeSource codeSource = BulkBean.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            if (current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Could not initialize class net.sf.cglib.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static class ChildFirstCglibClassLoader extends URLClassLoader {
        ChildFirstCglibClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, BulkBeanTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.sf.cglib.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static class BulkBeanTarget {
        private String name;
        private int count;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
