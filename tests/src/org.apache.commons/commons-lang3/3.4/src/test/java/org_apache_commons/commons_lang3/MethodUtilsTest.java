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
    public void invokeMethodAndInvokeStaticMethodResolveCompatiblePrimitiveParameters() throws Exception {
        PrimitiveTarget target = new PrimitiveTarget();

        Object instanceResult = MethodUtils.invokeMethod(target, "add", new Object[]{4, 5},
                new Class<?>[]{Integer.class, Integer.class});
        Object staticResult = MethodUtils.invokeStaticMethod(PrimitiveTarget.class, "multiply", new Object[]{3, 7},
                new Class<?>[]{Integer.class, Integer.class});

        assertThat(instanceResult).isEqualTo("sum:9");
        assertThat(staticResult).isEqualTo("product:21");
    }

    @Test
    public void invokeExactMethodsUseAccessiblePublicLookup() throws Exception {
        ExactTarget target = new ExactTarget();

        Object instanceResult = MethodUtils.invokeExactMethod(target, "echo", new Object[]{"commons"},
                new Class<?>[]{String.class});
        Object staticResult = MethodUtils.invokeExactStaticMethod(ExactTarget.class, "echoStatic", new Object[]{"lang"},
                new Class<?>[]{String.class});

        assertThat(instanceResult).isEqualTo("echo:commons");
        assertThat(staticResult).isEqualTo("echo:LANG");
    }

    @Test
    public void getAccessibleMethodFindsPublicDeclarationsFromInterfaceAndSuperclass() {
        Method interfaceMethod = MethodUtils.getAccessibleMethod(PackagePrivateGreetingTarget.class, "greet",
                String.class);
        Method superclassMethod = MethodUtils.getAccessibleMethod(PackagePrivateOverride.class, "inheritedEcho",
                String.class);

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
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void getMethodsListWithAnnotationFindsInheritedPublicAnnotations() {
        List<Method> methods = MethodUtils.getMethodsListWithAnnotation(AnnotatedChild.class, Marker.class);

        assertThat(methods).extracting(Method::getName).contains("parentPublicAnnotated", "childPublicAnnotated");
    }

    public interface Greeting {
        String greet(String name);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Marker {
    }

    public static class PrimitiveTarget {
        public String add(int left, int right) {
            return "sum:" + (left + right);
        }

        public static String multiply(int left, int right) {
            return "product:" + (left * right);
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

    public static class AnnotatedParent {
        @Marker
        public void parentPublicAnnotated() {
        }
    }

    public static class AnnotatedChild extends AnnotatedParent {
        @Marker
        public void childPublicAnnotated() {
        }

        public void childPlainMethod() {
        }
    }
}
