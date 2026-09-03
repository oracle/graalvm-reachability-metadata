/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.collections.impl.utility.ArrayListIterate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayListIterateTest {

    @Test
    void removeIfCompactsArrayListInPlace() {
        ArrayList<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));

        boolean changed = ArrayListIterate.removeIf(numbers, each -> each % 2 == 0);

        assertThat(changed).isTrue();
        assertThat(numbers).containsExactly(1, 3, 5);

        numbers.add(7);
        assertThat(numbers).containsExactly(1, 3, 5, 7);
    }
}
