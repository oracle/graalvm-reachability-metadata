/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListerTest {
    @Test
    public void createsParameterizedTypeWithRequestedRawTypeAndArgument() {
        final ParameterizedType type = new ParameterizedTypeImpl(ArrayList.class, String.class);

        assertThat(type.getRawType()).isEqualTo(ArrayList.class);
        assertThat(type.getActualTypeArguments()).containsExactly(String.class);
        assertThat(type.getOwnerType()).isNull();
    }

    @Test
    public void comparesParameterizedTypesByRawTypeAndArguments() {
        final ParameterizedType stringList = new ParameterizedTypeImpl(List.class, String.class);
        final ParameterizedType anotherStringList = new ParameterizedTypeImpl(List.class, String.class);
        final ParameterizedType integerList = new ParameterizedTypeImpl(List.class, Integer.class);

        assertThat(stringList).isEqualTo(anotherStringList);
        assertThat(stringList).hasSameHashCodeAs(anotherStringList);
        assertThat(stringList).isNotEqualTo(integerList);
    }
}
