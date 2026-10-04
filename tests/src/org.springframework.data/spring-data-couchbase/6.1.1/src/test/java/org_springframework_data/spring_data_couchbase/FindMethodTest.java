/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.repository.support.FindMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class FindMethodTest {

    @Test
    void findsMostSpecificPublicMethod() throws NoSuchMethodException {
        Method method = FindMethod.findMethod(OverloadedMethods.class, "choose", new Class[] {String.class});

        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void findsDeclaredMethod() throws NoSuchMethodException {
        Method method = FindMethod.findDeclaredMethod(OverloadedMethods.class, "declared", new Class[] {String.class});

        assertThat(method.getName()).isEqualTo("declared");
    }

    public static class OverloadedMethods {

        public void choose(Object value) {
        }

        public void choose(CharSequence value) {
        }

        public void choose(String value) {
        }

        private void declared(String value) {
        }
    }
}
