/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.json.JSON;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class J2oVisitorTest {
    @Test
    void parsesObjectsCollectionsAndTypedArrays() throws Exception {
        Record record =
                JSON.parse("{\"name\":\"dubbo\",\"numbers\":[1,2,3]}", Record.class);
        int[] values = JSON.parse("[4,5]", int[].class);

        assertThat(record.name).isEqualTo("dubbo");
        assertThat(record.numbers).containsExactly(1, 2, 3);
        assertThat(values).containsExactly(4, 5);
    }

    public static class Record {
        public String name;
        public ArrayList<Integer> numbers;
    }
}
