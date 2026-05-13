/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.lang.reflect.Method;

import org.apache.maven.shared.utils.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapTest {
    @Test
    void findsMethodDeclaredByNonPublicClassThroughPublicInterface() throws Exception {
        ClassMap classMap = new ClassMap(PackagePrivateImplementation.class);

        Method method = classMap.findMethod("message", "forge");

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(PublicContract.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    @Test
    void upcastsMethodFromNonPublicInterfaceToPublicParentInterface() throws Exception {
        ClassMap classMap = new ClassMap(PublicCompositeContract.class);

        Method method = classMap.findMethod("message", "forge");

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(PublicContract.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    public interface PublicContract {
        String message(String value);
    }

    interface PackagePrivateChildContract extends PublicContract {
        @Override
        String message(String value);
    }

    public interface PublicCompositeContract extends PackagePrivateChildContract {
    }

    static class PackagePrivateImplementation implements PublicContract {
        @Override
        public String message(String value) {
            return "covered-" + value;
        }
    }
}
