/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HsqlArrayList;
import org.junit.jupiter.api.Test;

public class ArrayUtilTest {
    @Test
    void duplicateArrayCreatesDistinctArrayWithSameComponentTypeAndValues() {
        HsqlArrayList alpha = listWith("alpha");
        HsqlArrayList beta = listWith("beta");
        HsqlArrayList gamma = listWith("gamma");
        HsqlArrayList[] source = new HsqlArrayList[] { alpha, beta, gamma };

        Object duplicated = ArrayUtil.duplicateArray(source);

        assertThat(duplicated).isInstanceOf(HsqlArrayList[].class);
        assertThat(duplicated).isNotSameAs(source);
        assertThat((HsqlArrayList[]) duplicated).containsExactly(alpha, beta, gamma);
    }

    @Test
    void resizeArrayAlwaysCreatesNewArrayAndPreservesFittingElements() {
        HsqlArrayList first = listWith("first");
        HsqlArrayList second = listWith("second");
        HsqlArrayList third = listWith("third");
        HsqlArrayList[] source = new HsqlArrayList[] { first, second, third };

        Object enlarged = ArrayUtil.resizeArray(source, 5);
        Object shortened = ArrayUtil.resizeArray(source, 2);

        assertThat(enlarged).isInstanceOf(HsqlArrayList[].class);
        assertThat(enlarged).isNotSameAs(source);
        assertThat((HsqlArrayList[]) enlarged).containsExactly(first, second, third, null, null);
        assertThat((HsqlArrayList[]) shortened).containsExactly(first, second);
    }

    @Test
    void resizeArrayIfDifferentReusesSameSizedArrayAndCopiesWhenSizeChanges() {
        HsqlArrayList first = listWith("first");
        HsqlArrayList second = listWith("second");
        HsqlArrayList[] source = new HsqlArrayList[] { first, second };

        Object sameSize = ArrayUtil.resizeArrayIfDifferent(source, source.length);
        Object enlarged = ArrayUtil.resizeArrayIfDifferent(source, 3);
        Object shortened = ArrayUtil.resizeArrayIfDifferent(source, 1);

        assertThat(sameSize).isSameAs(source);
        assertThat(enlarged).isInstanceOf(HsqlArrayList[].class);
        assertThat(enlarged).isNotSameAs(source);
        assertThat((HsqlArrayList[]) enlarged).containsExactly(first, second, null);
        assertThat((HsqlArrayList[]) shortened).containsExactly(first);
    }

    @Test
    void toAdjustedArrayAddsAndRemovesElementsWithoutChangingSourceArray() {
        HsqlArrayList first = listWith("first");
        HsqlArrayList second = listWith("second");
        HsqlArrayList third = listWith("third");
        HsqlArrayList[] source = new HsqlArrayList[] { first, third };

        Object added = ArrayUtil.toAdjustedArray(source, second, 1, 1);
        Object removed = ArrayUtil.toAdjustedArray((HsqlArrayList[]) added, null, 0, -1);

        assertThat(added).isInstanceOf(HsqlArrayList[].class);
        assertThat((HsqlArrayList[]) added).containsExactly(first, second, third);
        assertThat((HsqlArrayList[]) removed).containsExactly(second, third);
        assertThat(source).containsExactly(first, third);
    }

    private static HsqlArrayList listWith(String value) {
        HsqlArrayList list = new HsqlArrayList();

        list.add(value);

        return list;
    }
}
