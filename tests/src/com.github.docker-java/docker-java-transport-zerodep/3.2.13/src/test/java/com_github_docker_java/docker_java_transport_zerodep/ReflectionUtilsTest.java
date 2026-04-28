/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.language.DoubleMetaphone;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void callSetterAndGetterInvokePublicBeanMethods() {
        DoubleMetaphone encoder = new DoubleMetaphone();

        ReflectionUtils.callSetter(encoder, "MaxCodeLen", int.class, 8);
        Integer maxCodeLength = ReflectionUtils.callGetter(encoder, "MaxCodeLen", Integer.class);

        assertThat(maxCodeLength).isEqualTo(8);
        assertThat(encoder.doubleMetaphone("Washington")).hasSizeLessThanOrEqualTo(maxCodeLength);
    }
}
