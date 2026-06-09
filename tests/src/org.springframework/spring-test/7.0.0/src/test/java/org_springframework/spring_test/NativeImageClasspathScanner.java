/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URI;
import java.util.List;

import org.junit.platform.commons.support.scanning.ClassFilter;
import org.junit.platform.commons.support.scanning.ClasspathScanner;

public class NativeImageClasspathScanner implements ClasspathScanner {
    private static final List<Class<?>> DISCOVERABLE_CLASSES = List.of(
            DefaultMethodInvokerTest.class,
            MergedContextConfigurationRuntimeHintsTest.class,
            ProfileValueUtilsTest.class,
            SimpleNamingContextBuilderTest.class,
            SpringJUnit4ClassRunnerTest.class,
            TestBeanOverrideHandlerTest.class,
            TestClassScannerTest.class,
            TestContextManagerTest.class
    );

    @Override
    public List<Class<?>> scanForClassesInPackage(String packageName, ClassFilter classFilter) {
        return filterClasses(packageName, classFilter);
    }

    @Override
    public List<Class<?>> scanForClassesInClasspathRoot(URI uri, ClassFilter classFilter) {
        return filterClasses(null, classFilter);
    }

    private static List<Class<?>> filterClasses(String packageName, ClassFilter classFilter) {
        return DISCOVERABLE_CLASSES.stream()
                .filter((clazz) -> packageName == null || clazz.getPackageName().startsWith(packageName))
                .filter((clazz) -> classFilter.match(clazz.getName()))
                .filter(classFilter::match)
                .toList();
    }
}
