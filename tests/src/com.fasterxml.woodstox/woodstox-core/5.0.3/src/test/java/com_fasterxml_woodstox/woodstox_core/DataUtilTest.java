/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import com.ctc.wstx.util.DataUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataUtilTest {

    @Test
    void growArrayBy50PctKeepsArrayComponentTypeAndContents() {
        String[] original = {"alpha", "beta", "gamma", "delta"};

        Object grown = DataUtil.growArrayBy50Pct(original);

        assertThat(grown).isInstanceOf(String[].class);
        assertThat((String[]) grown)
                .hasSize(6)
                .startsWith(original);
    }

    @Test
    void growArrayToAtLeastUsesRequestedMinimumWhenLargerThanDefaultGrowth() {
        int[] original = {1, 2, 3};

        Object grown = DataUtil.growArrayToAtLeast(original, 8);

        assertThat(grown).isInstanceOf(int[].class);
        assertThat((int[]) grown)
                .hasSize(8)
                .startsWith(original);
    }
}
