/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.lib.HsqlArrayList;
import org.junit.jupiter.api.Test;

public class HsqlArrayListTest {
    @Test
    void addBeyondCurrentCapacityResizesBackingArray() {
        HsqlArrayList alpha = listWith("alpha");
        HsqlArrayList beta = listWith("beta");
        HsqlArrayList list = new HsqlArrayList(new HsqlArrayList[] { alpha }, 1);

        list.add(beta);

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getArray()).isInstanceOf(HsqlArrayList[].class);
        assertThat((HsqlArrayList[]) list.getArray()).containsExactly(alpha, beta);
    }

    @Test
    void trimResizesBackingArrayToElementCount() {
        HsqlArrayList list = new HsqlArrayList();

        list.add("alpha");
        list.add("beta");
        assertThat(list.getArray()).hasSize(8);

        list.trim();

        assertThat(list.getArray()).hasSize(2);
        assertThat(list.getArray()).containsExactly("alpha", "beta");
    }

    @Test
    void toArrayCopiesElementsToANewArray() {
        HsqlArrayList list = new HsqlArrayList();

        list.add("alpha");
        list.add("beta");
        list.add("gamma");

        Object[] copy = list.toArray();

        assertThat(copy).isNotSameAs(list.getArray());
        assertThat(copy).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void toArrayPreservesBackingArrayComponentType() {
        HsqlArrayList alpha = listWith("alpha");
        HsqlArrayList beta = listWith("beta");
        HsqlArrayList gamma = listWith("gamma");
        HsqlArrayList list = new HsqlArrayList(new HsqlArrayList[] { alpha, beta, gamma }, 3);

        Object[] copy = list.toArray();
        Object[] rangeCopy = list.toArray(1, 3);
        Object typedCopy = list.toArray(new HsqlArrayList[1]);

        assertThat(copy).isInstanceOf(HsqlArrayList[].class);
        assertThat((HsqlArrayList[]) copy).containsExactly(alpha, beta, gamma);
        assertThat(rangeCopy).isInstanceOf(HsqlArrayList[].class);
        assertThat((HsqlArrayList[]) rangeCopy).containsExactly(beta, gamma);
        assertThat(typedCopy).isInstanceOf(HsqlArrayList[].class);
        assertThat((HsqlArrayList[]) typedCopy).containsExactly(alpha, beta, gamma);
    }

    private static HsqlArrayList listWith(String value) {
        HsqlArrayList list = new HsqlArrayList();

        list.add(value);

        return list;
    }
}
