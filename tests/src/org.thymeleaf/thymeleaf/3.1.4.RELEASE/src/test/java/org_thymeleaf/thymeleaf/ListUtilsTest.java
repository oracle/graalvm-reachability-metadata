/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.ListUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ListUtilsTest {

    @Test
    void sortCreatesSortedLinkedListUsingDefaultConstructor() {
        LinkedList<String> original = new LinkedList<>(List.of("gamma", "alpha", "beta"));

        List<String> result = ListUtils.sort(original);

        assertThat(result).isInstanceOf(LinkedList.class);
        assertThat(result).containsExactly("alpha", "beta", "gamma");
        assertThat(result).isNotSameAs(original);
        assertThat(original).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    void sortWithComparatorCreatesSortedLinkedListUsingDefaultConstructor() {
        LinkedList<String> original = new LinkedList<>(List.of("alpha", "gamma", "beta"));

        List<String> result = ListUtils.sort(original, Comparator.reverseOrder());

        assertThat(result).isInstanceOf(LinkedList.class);
        assertThat(result).containsExactly("gamma", "beta", "alpha");
        assertThat(result).isNotSameAs(original);
        assertThat(original).containsExactly("alpha", "gamma", "beta");
    }
}
