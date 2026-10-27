/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.collections.ArrayHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayHelperTest {

    @Test
    public void createsTypedFilledAndJoinedArrays() {
        String[] filled = ArrayHelper.filledArray("hibernate", String.class, 2);
        Number[] joined = ArrayHelper.join(
                new Number[]{1},
                Integer.valueOf(2),
                Long.valueOf(3)
        );

        assertThat(filled).containsExactly("hibernate", "hibernate");
        assertThat(joined).containsExactly(1, 2, 3L);
    }
}
