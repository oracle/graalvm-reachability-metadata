/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    void convertsClassNamesToClassesWhenNamesExistAndWhenTheyDoNot() {
        final List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(List.of(
                String.class.getName(),
                PublicInnerLookupTarget.class.getName(),
                "missing.Type"
        ));

        assertThat(classes).containsExactly(String.class, PublicInnerLookupTarget.class, null);
    }

    @Test
    void resolvesClassesFromSourceStyleInnerClassNames() throws ClassNotFoundException {
        final Class<?> resolvedClass = ClassUtils.getClass(PublicInnerLookupTarget.class.getCanonicalName());

        assertThat(resolvedClass).isEqualTo(PublicInnerLookupTarget.class);
    }

    @Test
    void findsPublicMethodsDeclaredOnPublicInterfacesAndSuperclasses() throws NoSuchMethodException {
        final Method interfaceMethod = ClassUtils.getPublicMethod(
                PackagePrivateInterfaceImplementation.class,
                "execute",
                String.class
        );
        final Method superclassMethod = ClassUtils.getPublicMethod(
                PackagePrivateOverride.class,
                "overrideMe",
                String.class
        );

        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(PublicOperation.class);
        assertThat(superclassMethod.getDeclaringClass()).isEqualTo(PublicBase.class);
    }

    public static class PublicInnerLookupTarget {
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
}
