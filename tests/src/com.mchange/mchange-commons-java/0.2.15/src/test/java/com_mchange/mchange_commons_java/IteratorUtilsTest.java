/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.util.List;

import com.mchange.v1.util.IteratorUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IteratorUtilsTest {
    @Test
    void toArrayCreatesTypedArrayFromIteratorAndComponentClass() {
        Object[] values = IteratorUtils.toArray(List.of("alpha", "beta").iterator(), 3, String.class, true);

        assertThat(values).isInstanceOf(String[].class);
        assertThat((String[]) values).containsExactly("alpha", "beta", null);
    }

    @Test
    void toArrayAllocatesReplacementArrayWhenProvidedArrayIsTooSmall() {
        String[] provided = new String[1];

        Object[] values = IteratorUtils.toArray(List.of("alpha", "beta").iterator(), 2, provided);

        assertThat(values).isInstanceOf(String[].class).isNotSameAs(provided);
        assertThat((String[]) values).containsExactly("alpha", "beta");
    }
}
