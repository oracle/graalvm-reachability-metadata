/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.validator.jtype.TypeUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    public void createsArrayTypeFromComponentClass() {
        assertThat(TypeUtils.getArrayType(String.class)).isEqualTo(String[].class);
    }
}
