/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.Test;

public class MethodUtilsTest {

    @Test
    public void invokeMethodAndInvokeStaticMethodHandlePrimitiveVarArgs() throws Exception {
        VarArgsTarget target = new VarArgsTarget();

        Object instanceResult = MethodUtils.invokeMethod(target, "join", new Object[]{"sum", 1, 2, 3},
                new Class<?>[]{String.class, Integer.class, Integer.class, Integer.class});
        Object staticResult = MethodUtils.invokeStaticMethod(VarArgsTarget.class, "joinStatic", new Object[]{"sum", 4, 5},
                new Class<?>[]{String.class, Integer.class, Integer.class});

        assertThat(instanceResult).isEqualTo("sum:6");
        assertThat(staticResult).isEqualTo("sum:9");
    }

    @Test
    public void invokeExactMethodsUseAccessibleMethodLookup() throws Exception {
        ExactTarget target = new ExactTarget();

        Object instanceResult = MethodUtils.invokeExactMethod(target, "echo", new Object[]{"commons"},
                new Class<?>[]{String.class});
        Object staticResult = MethodUtils.invokeExactStaticMethod(ExactTarget.class, "echoStatic", new Object[]{"lang"},
                new Class<?>[]{String.class});

        assertThat(instanceResult).isEqualTo("echo:commons");
        assertThat(staticResult).isEqualTo("echo:LANG");
    }

    @Test
    public void getAccessibleMethodFindsPublicDeclarationsFromInterfaceAndSuperclass() throws Exception {
        Method interfaceMethod = MethodUtils.getAccessibleMethod(
                PackagePrivateGreetingTarget.class.getDeclaredMethod("greet", String.class));
        Method superclassMethod = MethodUtils.getAccessibleMethod(
                PackagePrivateOverride.class.getDeclaredMethod("inheritedEcho", String.class));

        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Greeting.class);
        assertThat(superclassMethod).isNotNull();
        assertThat(superclassMethod.getDeclaringClass()).isEqualTo(PublicBase.class);
    }

    @Test
    public void getMatchingAccessibleMethodUsesDirectPublicLookupWhenSignatureMatches() {
        Method method = MethodUtils.getMatchingAccessibleMethod(ExactTarget.class, "echo", String.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(ExactTarget.class);
        assertThat(method.getName()).isEqualTo("echo");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void getMatchingMethodSearchesDeclaredMethodsInClassHierarchy() {
        Method method = MethodUtils.getMatchingMethod(OverloadedChild.class, "describe", Integer.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(OverloadedChild.class);
        assertThat(method.getParameterTypes()).containsExactly(Integer.class);
    }

    @Test
    public void getMethodsListWithAnnotationSupportsPublicAndDeclaredModes() {
        List<Method> publicMethods = MethodUtils.getMethodsListWithAnnotation(AnnotatedChild.class, Marker.class, true, false);
        List<Method> declaredMethods = MethodUtils.getMethodsListWithAnnotation(AnnotatedChild.class, Marker.class, true, true);

        assertThat(publicMethods).extracting(Method::getName).contains("parentPublicAnnotated");
        assertThat(publicMethods).extracting(Method::getName).doesNotContain("childPrivateAnnotated");
        assertThat(declaredMethods).extracting(Method::getName).contains("parentPublicAnnotated", "childPrivateAnnotated");
    }

    public interface Greeting {
        String greet(String name);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Marker {
    }

    public static class VarArgsTarget {
        public String join(String prefix, int... values) {
            int sum = 0;
            for (int value : values) {
                sum += value;
            }
            return prefix + ":" + sum;
        }

        public static String joinStatic(String prefix, int... values) {
            int sum = 0;
            for (int value : values) {
                sum += value;
            }
            return prefix + ":" + sum;
        }
    }

    public static class ExactTarget {
        public String echo(String value) {
            return "echo:" + value;
        }

        public static String echoStatic(String value) {
            return "echo:" + value.toUpperCase();
        }
    }

    static class PackagePrivateGreetingTarget implements Greeting {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class PublicBase {
        public String inheritedEcho(String value) {
            return "base:" + value;
        }
    }

    static class PackagePrivateOverride extends PublicBase {
        @Override
        public String inheritedEcho(String value) {
            return "override:" + value;
        }
    }

    public static class OverloadedParent {
        public String describe(Number value) {
            return "parent:" + value;
        }
    }

    public static class OverloadedChild extends OverloadedParent {
        public String describe(Integer value) {
            return "child:" + value;
        }
    }

    public static class AnnotatedParent {
        @Marker
        public void parentPublicAnnotated() {
        }
    }

    public static class AnnotatedChild extends AnnotatedParent {
        @Marker
        private void childPrivateAnnotated() {
        }

        public void childPlainMethod() {
        }
    }
}
