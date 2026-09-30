/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.algorithm.ObjectUtility;
import org.junit.jupiter.api.Test;

public class ObjectUtilityTest {
    public static class Record {
        private final String name = "sample";
        private final int number = 7;
    }

    @Test
    public void formatsObjectFields() {
        String result = new ObjectUtility(true, 0, 2).objectToString(new Record());

        assertThat(result).contains("sample").contains("7");
    }
}
