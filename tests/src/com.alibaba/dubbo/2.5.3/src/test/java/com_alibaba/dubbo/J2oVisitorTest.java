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
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class J2oVisitorTest {
    @Test
    void parsesMapsCollectionsAndTypedArrays() throws Exception {
        HashMap<?, ?> map = JSON.parse("{\"name\":\"dubbo\"}", HashMap.class);
        ArrayList<?> list = JSON.parse("[1,2,3]", ArrayList.class);
        int[] values = JSON.parse("[4,5]", int[].class);

        assertThat(map).containsEntry("name", "dubbo");
        assertThat(list).containsExactly(1L, 2L, 3L);
        assertThat(values).containsExactly(4, 5);
    }
}
