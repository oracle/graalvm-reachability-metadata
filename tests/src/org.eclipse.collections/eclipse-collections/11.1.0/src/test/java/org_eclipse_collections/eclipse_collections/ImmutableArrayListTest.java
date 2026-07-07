/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableArrayListTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        ImmutableList<String> list = Lists.immutable.with(
                "zero",
                "one",
                "two",
                "three",
                "four",
                "five",
                "six",
                "seven",
                "eight",
                "nine",
                "ten");
        String[] target = new String[0];

        String[] result = list.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .containsExactly(
                        "zero",
                        "one",
                        "two",
                        "three",
                        "four",
                        "five",
                        "six",
                        "seven",
                        "eight",
                        "nine",
                        "ten");
        assertThat(result.getClass()).isEqualTo(String[].class);
    }
}
