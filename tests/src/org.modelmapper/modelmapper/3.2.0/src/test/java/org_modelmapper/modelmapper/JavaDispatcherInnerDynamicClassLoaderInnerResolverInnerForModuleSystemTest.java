/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher;

public class JavaDispatcherInnerDynamicClassLoaderInnerResolverInnerForModuleSystemTest {
    @Test
    void invokesModuleExportAdjustmentForNonExportedPackage() throws Exception {
        Class<?> target = JavaDispatcherInnerDynamicClassLoaderInnerResolverInnerForModuleSystemTest.class;
        String packageName = target.getPackageName();
        SyntheticModuleSupport.reset();

        new DispatcherAccess().acceptWithSyntheticModuleResolver(getClass().getClassLoader(), target);

        assertThat(SyntheticModuleSupport.isExportedPackageName).isEqualTo(packageName);
        assertThat(SyntheticModuleSupport.addExportsPackageName).isEqualTo(packageName);
        assertThat(SyntheticModuleSupport.addExportsTargetModule)
            .isSameAs(SyntheticModuleSupport.UNNAMED_MODULE);
    }

    private interface SampleDispatcher {
    }

    private static final class DispatcherAccess extends JavaDispatcher<SampleDispatcher> {
        private DispatcherAccess() {
            super(
                SampleDispatcher.class,
                JavaDispatcherInnerDynamicClassLoaderInnerResolverInnerForModuleSystemTest.class.getClassLoader(),
                false);
        }

        private void acceptWithSyntheticModuleResolver(ClassLoader classLoader, Class<?> target) throws Exception {
            new ResolverAccess.SyntheticResolver(
                SyntheticModuleSupport.class.getMethod("getModule"),
                SyntheticModuleSupport.class.getMethod("isExported", String.class),
                SyntheticModuleSupport.class.getMethod("addExports", String.class, Object.class),
                SyntheticModuleSupport.class.getMethod("getUnnamedModule"))
                .accept(classLoader, target);
        }

        private static class ResolverAccess extends DynamicClassLoader {
            private ResolverAccess(Class<?> target) {
                super(target);
            }

            private static final class SyntheticResolver extends Resolver.ForModuleSystem {
                private SyntheticResolver(
                    Method getModule,
                    Method isExported,
                    Method addExports,
                    Method getUnnamedModule) {
                    super(getModule, isExported, addExports, getUnnamedModule);
                }
            }
        }
    }

    public static final class SyntheticModuleSupport {
        private static final Object MODULE = new Object();
        private static final Object UNNAMED_MODULE = new Object();

        private static String isExportedPackageName;
        private static String addExportsPackageName;
        private static Object addExportsTargetModule;

        private SyntheticModuleSupport() {
        }

        public static Object getModule() {
            return MODULE;
        }

        public static Boolean isExported(String packageName) {
            isExportedPackageName = packageName;
            return Boolean.FALSE;
        }

        public static Object addExports(String packageName, Object targetModule) {
            addExportsPackageName = packageName;
            addExportsTargetModule = targetModule;
            return MODULE;
        }

        public static Object getUnnamedModule() {
            return UNNAMED_MODULE;
        }

        private static void reset() {
            isExportedPackageName = null;
            addExportsPackageName = null;
            addExportsTargetModule = null;
        }
    }
}
