/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.ComparisonFailure;
import org.junit.jupiter.api.Test;

import com.diffplug.common.tree.TreeComparison;
import com.diffplug.common.tree.TreeNode;

public class TreeComparisonTest {
    @Test
    void assertionMismatchCreatesJUnitComparisonFailure() {
        TreeNode<String> expected = TreeNode.createTestData(
                "root",
                " child",
                "  expected-leaf");
        TreeNode<String> actual = TreeNode.createTestData(
                "root",
                " child",
                "  actual-leaf");

        ComparisonFailure failure = assertThrows(ComparisonFailure.class,
                () -> TreeComparison.of(expected, actual)
                        .decorateErrorsWith(value -> "node:" + value)
                        .assertEqual());

        assertThat(failure.getExpected()).contains("node:expected-leaf");
        assertThat(failure.getActual()).contains("node:actual-leaf");
    }
}
