/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_gmbal.gmbal_api_only;

import org.glassfish.gmbal.util.GenericConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericConstructorTest {
    @Test
    void createLoadsClassByNameAndInvokesConstructor() {
        GenericConstructor<CharSequence> constructor = new GenericConstructor<>(
                CharSequence.class,
                StringBuilder.class.getName(),
                String.class);

        CharSequence result = constructor.create("created through GenericConstructor");

        assertThat(result).isInstanceOf(StringBuilder.class);
        assertThat(result.toString()).isEqualTo("created through GenericConstructor");
    }
}
