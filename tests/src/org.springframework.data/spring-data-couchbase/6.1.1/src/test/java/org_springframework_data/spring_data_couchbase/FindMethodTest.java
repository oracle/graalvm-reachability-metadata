/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.couchbase.repository.support.FindMethod;

@Timeout(60)
public class FindMethodTest {

    @Test
    void selectsApplicablePublicAndDeclaredMethods() throws Exception {
        assertThat(FindMethod.findMethod(Methods.class, "convert", new Class<?>[] {Integer.class})
                .getParameterTypes()).containsExactly(Number.class);
        assertThat(FindMethod.findDeclaredMethod(Methods.class, "hidden", new Class<?>[] {String.class})
                .getParameterTypes()).containsExactly(String.class);
    }

    public static class Methods {
        public String convert(Number value) {
            return value.toString();
        }

        public String convert(Object value) {
            return value.toString();
        }

        private String hidden(String value) {
            return value;
        }
    }
}
