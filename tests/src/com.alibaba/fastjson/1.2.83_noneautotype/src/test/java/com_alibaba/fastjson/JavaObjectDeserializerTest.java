/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.util.List;
import org.junit.jupiter.api.Test;

public class JavaObjectDeserializerTest {
    @Test
    void parseObjectDeserializesGenericArrayTypes() {
        List<String>[] values = JSON.parseObject("[[\"alpha\",\"beta\"],[\"gamma\"]]",
                new TypeReference<List<String>[]>() { });

        assertThat(values).hasSize(2);
        assertThat(values[0]).containsExactly("alpha", "beta");
        assertThat(values[1]).containsExactly("gamma");
    }
}
