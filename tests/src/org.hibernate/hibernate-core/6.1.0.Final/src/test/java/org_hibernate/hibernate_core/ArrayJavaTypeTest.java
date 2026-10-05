/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.ArrayJavaType;
import org.hibernate.type.descriptor.java.IntegerJavaType;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayJavaTypeTest {

    @Test
    public void parsesWrapsAndUnwrapsObjectArrays() {
        ArrayJavaType<String> type = new ArrayJavaType<>(StringJavaType.INSTANCE);

        assertThat(type.fromString("{\"one\",\"two\"}"))
                .containsExactly("one", "two");
        assertThat(type.unwrap(new String[]{"one", "two"}, CharSequence[].class, null))
                .containsExactly("one", "two");
        ArrayJavaType<Integer> integerType = new ArrayJavaType<>(IntegerJavaType.INSTANCE);
        assertThat(integerType.unwrap(new Integer[]{1, 2}, Long[].class, null))
                .containsExactly(1L, 2L);
        assertThat(type.wrap(new CharSequence[]{"one", "two"}, null))
                .containsExactly("one", "two");
    }
}
