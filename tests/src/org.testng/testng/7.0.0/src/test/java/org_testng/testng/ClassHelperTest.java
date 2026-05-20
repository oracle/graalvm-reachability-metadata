/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testng.IClass;
import org.testng.IMethodSelector;
import org.testng.IMethodSelectorContext;
import org.testng.IObjectFactory;
import org.testng.ITestNGMethod;
import org.testng.annotations.IAnnotation;
import org.testng.internal.ClassHelper;
import org.testng.internal.ConstructorOrMethod;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlMethodSelector;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

public class ClassHelperTest {
    @Test
    void createsInstancesThroughClassAndConstructorHelpers() throws Exception {
        SimpleTestClass directInstance = ClassHelper.newInstance(SimpleTestClass.class);
        assertThat(directInstance).isNotNull();

        SimpleTestClass instanceOrNull = ClassHelper.newInstanceOrNull(SimpleTestClass.class);
        assertThat(instanceOrNull).isNotNull();

        Constructor<SimpleTestClass> constructor = SimpleTestClass.class.getConstructor();
        SimpleTestClass constructorInstance = ClassHelper.newInstance(constructor);
        assertThat(constructorInstance).isNotNull();
    }

    @Test
    void loadsClassesWithContextLoaderAndFallbackForNamePaths() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassHelperTest.class.getClassLoader());
            assertThat(ClassHelper.forName(SimpleTestClass.class.getName())).isSameAs(SimpleTestClass.class);

            Thread.currentThread().setContextClassLoader(null);
            assertThat(ClassHelper.forName(StringConstructorTestClass.class.getName()))
                    .isSameAs(StringConstructorTestClass.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    void createsMethodSelectorFromXmlSelectorClassName() {
        XmlMethodSelector selector = new XmlMethodSelector();
        selector.setClassName(AcceptAllSelector.class.getName());

        IMethodSelector methodSelector = ClassHelper.createSelector(selector);

        assertThat(methodSelector).isInstanceOf(AcceptAllSelector.class);
        assertThat(methodSelector.includeMethod(null, null, true)).isTrue();
    }

    @Test
    void findsFactoryMethodsAcrossDeclaredConstructorsAndInheritedMethods() {
        List<ConstructorOrMethod> factories = ClassHelper.findDeclaredFactoryMethods(
                FactoryChildClass.class,
                new EmptyAnnotationFinder());

        assertThat(factories).isEmpty();
        assertThat(ClassHelper.getAvailableMethods(FactoryChildClass.class))
                .extracting(Method::getName)
                .contains("baseMethod");
    }

    @Test
    void createsInnerClassInstanceUsingEnclosingClassFallback() {
        Object instance = ClassHelper.createInstance1(
                InnerTestClass.class,
                new HashMap<Class<?>, IClass>(),
                xmlTest("inner-instance"),
                new EmptyAnnotationFinder(),
                new DelegatingObjectFactory());

        assertThat(instance).isInstanceOf(InnerTestClass.class);
    }

    @Test
    void reachesEnclosingClassConstructorLookupWhenTrackedClassHasNoCachedInstances() {
        Map<Class<?>, IClass> classes = new HashMap<>();
        classes.put(PackageEnclosingClass.class, new EmptyTrackedClass(PackageEnclosingClass.class));

        Object instance = ClassHelper.createInstance1(
                PackageEnclosingClass.InnerTestTarget.class,
                classes,
                xmlTest("missing-enclosing-instance"),
                new EmptyAnnotationFinder(),
                new SelfSupplyingObjectFactory());

        assertThat(instance).isInstanceOf(PackageEnclosingClass.InnerTestTarget.class);
    }

    @Test
    void createsClassUsingStringConstructorFallback() {
        Object instance = ClassHelper.createInstance1(
                StringConstructorTestClass.class,
                new HashMap<Class<?>, IClass>(),
                xmlTest("string-constructor-name"),
                new EmptyAnnotationFinder(),
                new DelegatingObjectFactory());

        assertThat(instance).isInstanceOf(StringConstructorTestClass.class);
        assertThat(((StringConstructorTestClass) instance).name).isEqualTo("string-constructor-name");
    }

    @Test
    void triesAlternativeStringConstructor() {
        StringConstructorTestClass instance = ClassHelper.tryOtherConstructor(StringConstructorTestClass.class);

        assertThat(instance.name).isEqualTo("Default test name");
    }

    @Test
    void createsJUnitRunnerFallbackWhenNotifierIsUnavailable() {
        assertThat(ClassHelper.createTestRunner(null)).isNotNull();
    }

    private static XmlTest xmlTest(String name) {
        XmlSuite suite = new XmlSuite();
        XmlTest xmlTest = new XmlTest(suite);
        xmlTest.setName(name);
        return xmlTest;
    }

    public class InnerTestClass {
    }

    public static final class SimpleTestClass {
        public SimpleTestClass() {
        }
    }

    public static final class StringConstructorTestClass {
        private final String name;

        public StringConstructorTestClass(String name) {
            this.name = name;
        }
    }

    public static class FactoryBaseClass {
        public void baseMethod() {
        }
    }

    public static final class FactoryChildClass extends FactoryBaseClass {
        public FactoryChildClass() {
        }
    }

    public static final class AcceptAllSelector implements IMethodSelector {
        @Override
        public boolean includeMethod(IMethodSelectorContext context, ITestNGMethod method, boolean isTestMethod) {
            return true;
        }

        @Override
        public void setTestMethods(List<ITestNGMethod> testMethods) {
        }
    }

    private static final class DelegatingObjectFactory implements IObjectFactory {
        @Override
        public Object newInstance(Constructor constructor, Object... params) {
            return ClassHelper.newInstance(constructor, params);
        }
    }

    private static final class SelfSupplyingObjectFactory implements IObjectFactory {
        @Override
        public Object newInstance(Constructor constructor, Object... params) {
            if (constructor.getDeclaringClass().equals(PackageEnclosingClass.class)) {
                return new PackageEnclosingClass(null);
            }
            if (constructor.getDeclaringClass().equals(PackageEnclosingClass.InnerTestTarget.class)) {
                return ((PackageEnclosingClass) params[0]).new InnerTestTarget();
            }
            return ClassHelper.newInstance(constructor, params);
        }
    }

    private static final class EmptyTrackedClass implements IClass {
        private final Class<?> realClass;
        private final List<Object> instances = new ArrayList<>();

        private EmptyTrackedClass(Class<?> realClass) {
            this.realClass = realClass;
        }

        @Override
        public String getName() {
            return realClass.getName();
        }

        @Override
        public XmlTest getXmlTest() {
            return null;
        }

        @Override
        public XmlClass getXmlClass() {
            return null;
        }

        @Override
        public String getTestName() {
            return null;
        }

        @Override
        public Class<?> getRealClass() {
            return realClass;
        }

        @Override
        public Object[] getInstances(boolean create) {
            return instances.toArray(new Object[0]);
        }

        @Override
        @Deprecated
        public int getInstanceCount() {
            return instances.size();
        }

        @Override
        public long[] getInstanceHashCodes() {
            return new long[0];
        }

        @Override
        public void addInstance(Object instance) {
            instances.add(instance);
        }
    }

    private static final class EmptyAnnotationFinder implements IAnnotationFinder {
        @Override
        public <A extends IAnnotation> A findAnnotation(Class<?> cls, Class<A> annotationClass) {
            return null;
        }

        @Override
        public <A extends IAnnotation> A findAnnotation(Method method, Class<A> annotationClass) {
            return null;
        }

        @Override
        public <A extends IAnnotation> A findAnnotation(ITestNGMethod method, Class<A> annotationClass) {
            return null;
        }

        @Override
        public <A extends IAnnotation> A findAnnotation(ConstructorOrMethod com, Class<A> annotationClass) {
            return null;
        }

        @Override
        public <A extends IAnnotation> A findAnnotation(Constructor<?> constructor, Class<A> annotationClass) {
            return null;
        }

        @Override
        public boolean hasTestInstance(Method method, int index) {
            return false;
        }

        @Override
        public String[] findOptionalValues(Method method) {
            return new String[0];
        }

        @Override
        public String[] findOptionalValues(Constructor constructor) {
            return new String[0];
        }
    }
}

final class PackageEnclosingClass {
    public PackageEnclosingClass(PackageEnclosingClass ignored) {
    }

    public final class InnerTestTarget {
    }
}
