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
    void resolvesAccessibleMethodsFromInterfacesAndSuperclasses() {
        final Method interfaceMethod = MethodUtils.getAccessibleMethod(
                PackagePrivateInterfaceImplementation.class,
                "execute",
                String.class
        );
        final Method superclassMethod = MethodUtils.getAccessibleMethod(
                PackagePrivateOverride.class,
                "overrideMe",
                String.class
        );

        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(PublicOperation.class);
        assertThat(superclassMethod).isNotNull();
        assertThat(superclassMethod.getDeclaringClass()).isEqualTo(PublicBase.class);
    }

    @Test
    void invokesMatchingAndExactMethodsIncludingPrimitiveVarArgs() throws Exception {
        final InvocationTarget target = new InvocationTarget();

        assertThat(MethodUtils.getMatchingAccessibleMethod(
                InvocationTarget.class,
                "describe",
                CharSequence.class
        )).isNotNull();
        assertThat(MethodUtils.getMatchingAccessibleMethod(
                InvocationTarget.class,
                "describe",
                StringBuilder.class
        )).isNotNull();
        assertThat(MethodUtils.invokeMethod(target, "describe", new StringBuilder("value")))
                .isEqualTo("describe:value");
        assertThat(MethodUtils.invokeMethod(target, "sum", 1, 2, 3)).isEqualTo(6);
        assertThat(MethodUtils.invokeStaticMethod(InvocationTarget.class, "sumStatic", 4, 5, 6))
                .isEqualTo(15);
        assertThat(MethodUtils.invokeExactMethod(
                target,
                "exactEcho",
                new Object[] {"exact"},
                new Class<?>[] {String.class}
        )).isEqualTo("exact");
        assertThat(MethodUtils.invokeExactStaticMethod(
                InvocationTarget.class,
                "exactStaticEcho",
                new Object[] {"static"},
                new Class<?>[] {String.class}
        )).isEqualTo("static");
    }

    @Test
    void findsDeclaredMethodsAcrossTheClassHierarchy() {
        final Method matchingMethod = MethodUtils.getMatchingMethod(
                MatchingMethodChild.class,
                "transform",
                StringBuilder.class
        );

        assertThat(matchingMethod).isNotNull();
        assertThat(matchingMethod.getDeclaringClass()).isEqualTo(MatchingMethodParent.class);
    }

    @Test
    void listsAnnotatedMethodsForPublicAndDeclaredLookups() {
        final List<Method> publicAnnotatedMethods = MethodUtils.getMethodsListWithAnnotation(
                AnnotatedChild.class,
                Marker.class,
                true,
                false
        );
        final List<Method> declaredAnnotatedMethods = MethodUtils.getMethodsListWithAnnotation(
                AnnotatedChild.class,
                Marker.class,
                true,
                true
        );

        assertThat(publicAnnotatedMethods)
                .extracting(Method::getName)
                .contains("childPublic", "parentPublic")
                .doesNotContain("childHidden", "parentHidden");
        assertThat(declaredAnnotatedMethods)
                .extracting(Method::getName)
                .contains("childPublic", "parentPublic", "childHidden", "parentHidden");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Marker {
    }

    public interface PublicOperation {
        String execute(String value);
    }

    static class PackagePrivateInterfaceImplementation implements PublicOperation {
        @Override
        public String execute(final String value) {
            return "interface:" + value;
        }
    }

    public static class PublicBase {
        public String overrideMe(final String value) {
            return "base:" + value;
        }
    }

    static class PackagePrivateOverride extends PublicBase {
        @Override
        public String overrideMe(final String value) {
            return "override:" + value;
        }
    }

    public static class InvocationTarget {
        public String describe(final CharSequence value) {
            return "describe:" + value;
        }

        public int sum(final int... values) {
            int total = 0;
            for (final int value : values) {
                total += value;
            }
            return total;
        }

        public static int sumStatic(final int... values) {
            int total = 0;
            for (final int value : values) {
                total += value;
            }
            return total;
        }

        public String exactEcho(final String value) {
            return value;
        }

        public static String exactStaticEcho(final String value) {
            return value;
        }
    }

    public static class MatchingMethodParent {
        public String transform(final CharSequence value) {
            return value.toString();
        }
    }

    static class MatchingMethodChild extends MatchingMethodParent {
        public String transform(final Integer value) {
            return String.valueOf(value);
        }
    }

    public static class AnnotatedParent {
        @Marker
        public String parentPublic() {
            return "parent-public";
        }

        @Marker
        void parentHidden() {
        }
    }

    static class AnnotatedChild extends AnnotatedParent {
        @Marker
        public String childPublic() {
            return "child-public";
        }

        @Marker
        void childHidden() {
        }
    }
}
