/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class JavaObjectInputTest {
    @Test
    void readsObjectsWithTheJavaSerializationExtension() throws Exception {
        assertThat(SerializationTestSupport.roundTrip("java", Arrays.asList(1, 2, 3)))
                .isEqualTo(Arrays.asList(1, 2, 3));
    }
}
