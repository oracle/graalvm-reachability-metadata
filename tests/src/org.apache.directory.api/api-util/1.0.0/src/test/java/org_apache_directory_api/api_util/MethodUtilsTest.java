/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_api.api_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.directory.api.util.MethodUtils;
import org.junit.jupiter.api.Test;

public class MethodUtilsTest {

    @Test
    public void exactPublicSignatureIsReturned() throws Exception {
        Class<?>[] parameterTypes = new Class<?>[]{String.class};

        Method method = MethodUtils.getAssignmentCompatibleMethod(ExactTarget.class, "describe", parameterTypes);

        assertThat(method.getDeclaringClass()).isEqualTo(ExactTarget.class);
        assertThat(method.getName()).isEqualTo("describe");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void assignmentCompatibleSignatureIsFoundByScanningPublicMethods() throws Exception {
        Class<?>[] parameterTypes = new Class<?>[]{StringBuilder.class};

        Method method = MethodUtils.getAssignmentCompatibleMethod(AssignmentCompatibleTarget.class, "describe", parameterTypes);

        assertThat(method.getDeclaringClass()).isEqualTo(AssignmentCompatibleTarget.class);
        assertThat(method.getName()).isEqualTo("describe");
        assertThat(method.getParameterTypes()).containsExactly(CharSequence.class);
    }

    public static class ExactTarget {
        public String describe(String value) {
            return "exact:" + value;
        }
    }

    public static class AssignmentCompatibleTarget {
        public String describe(CharSequence value) {
            return "compatible:" + value;
        }
    }
}
