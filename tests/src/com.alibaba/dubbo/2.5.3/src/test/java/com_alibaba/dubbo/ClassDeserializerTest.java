/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

public class ClassDeserializerTest {
    @Test
    void restoresClassValuesByName() throws Exception {
        assertThat(HessianTestSupport.roundTrip(String.class)).isEqualTo(String.class);
        assertThat(
                        HessianTestSupport.roundTrip(
                                ApplicationType.class, new SerializerFactory(null)))
                .isEqualTo(ApplicationType.class);
    }

    public static class ApplicationType { }
}
