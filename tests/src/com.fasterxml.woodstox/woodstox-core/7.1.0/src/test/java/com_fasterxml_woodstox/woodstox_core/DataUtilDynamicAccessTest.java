/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.util.DataUtil;
import org.junit.jupiter.api.Test;

public class DataUtilDynamicAccessTest {
    @Test
    void growsArraysWithReflectiveAllocation() {
        String[] names = {"wood", "stox"};
        String[] grownByHalf = (String[]) DataUtil.growArrayBy50Pct(names);
        String[] grownToMinimum = (String[]) DataUtil.growArrayToAtLeast(names, 5);
        String[] grownToMaximum = (String[]) DataUtil.growArrayToAtMost(names, 3);

        assertThat(grownByHalf).hasSize(3).startsWith("wood", "stox");
        assertThat(grownToMinimum).hasSize(5).startsWith("wood", "stox");
        assertThat(grownToMaximum).hasSize(3).startsWith("wood", "stox");
    }
}
