/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.ListUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ListUtilsTest {

    @Test
    void sortCreatesANewInstanceOfTheConcreteListTypeForComparableElements() {
        InstantiableList<String> values = new InstantiableList<>();
        values.add("gamma");
        values.add("alpha");
        values.add("beta");

        List<String> result = ListUtils.sort(values);

        assertThat(result).isExactlyInstanceOf(InstantiableList.class);
        assertThat(result).containsExactly("alpha", "beta", "gamma");
        assertThat(result).isNotSameAs(values);
        assertThat(values).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    void sortWithComparatorCreatesANewInstanceOfTheConcreteListType() {
        InstantiableList<String> values = new InstantiableList<>();
        values.add("alpha");
        values.add("gamma");
        values.add("beta");

        List<String> result = ListUtils.sort(values, Comparator.reverseOrder());

        assertThat(result).isExactlyInstanceOf(InstantiableList.class);
        assertThat(result).containsExactly("gamma", "beta", "alpha");
        assertThat(result).isNotSameAs(values);
    }

    public static final class InstantiableList<T> extends ArrayList<T> {

        public InstantiableList() {
            super();
        }
    }
}
