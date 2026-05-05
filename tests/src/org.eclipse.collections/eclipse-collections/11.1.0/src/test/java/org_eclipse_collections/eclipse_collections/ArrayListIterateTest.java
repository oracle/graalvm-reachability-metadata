/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.collections.impl.utility.ArrayListIterate;
import org.junit.jupiter.api.Test;

public class ArrayListIterateTest {
    @Test
    void removeIfCompactsStandardArrayListInPlace() {
        final ArrayList<Integer> numbers = IntStream.range(0, 128)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));

        final boolean changed = ArrayListIterate.removeIf(numbers, each -> each % 3 == 0);

        final ArrayList<Integer> expected = IntStream.range(0, 128)
                .filter(each -> each % 3 != 0)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        assertThat(changed).isTrue();
        assertThat(numbers).containsExactlyElementsOf(expected);
    }
}
