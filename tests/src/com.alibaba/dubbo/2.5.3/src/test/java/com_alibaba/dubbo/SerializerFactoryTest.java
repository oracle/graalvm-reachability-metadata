/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SerializerFactoryTest {
    @Test
    void discoversCompanionSerializerByNamingConvention() throws Exception {
        JavaSerializerTest.Replacement replacement =
                (JavaSerializerTest.Replacement)
                        HessianTestSupport.roundTrip(
                                new JavaSerializerTest.FactoryValue("factory"),
                                JavaSerializerTest.Replacement.class);

        assertThat(replacement.value).isEqualTo("factory");
    }
}
