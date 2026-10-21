/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.validator.jtype.Types;
import org.junit.jupiter.api.Test;

public class TypesTest {
    @Test
    public void parsesNamedClassAsType() {
        assertThat(Types.valueOf("java.lang.String")).isEqualTo(String.class);
    }
}
