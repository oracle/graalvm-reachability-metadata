/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.ArrayType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeTest {

    @Test
    public void modelsAndResizesPersistentObjectArrays() {
        ArrayType type = new ArrayType("Record.values", null, String.class);

        assertThat(type.getReturnedClass()).isEqualTo(String[].class);
        assertThat((String[]) type.instantiateResult(new String[]{"one", "two"}))
                .hasSize(2)
                .containsOnlyNulls();
    }
}
