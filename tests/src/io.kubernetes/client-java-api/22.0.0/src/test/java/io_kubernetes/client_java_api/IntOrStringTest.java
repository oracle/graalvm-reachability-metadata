/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntOrStringTest {
    @Test
    void decodesAndEncodesIntegerValuesThroughClientJson() {
        IntOrString value = JSON.deserialize("8080", IntOrString.class);
        assertThat(value.isInteger()).isTrue();
        assertThat(value.getIntValue()).isEqualTo(8080);
        assertThat(JSON.serialize(value)).isEqualTo("8080");
    }

    @Test
    void decodesAndEncodesNamedValuesThroughClientJson() {
        IntOrString value = JSON.deserialize("\"http\"", IntOrString.class);
        assertThat(value.isInteger()).isFalse();
        assertThat(value.getStrValue()).isEqualTo("http");
        assertThat(JSON.serialize(value)).isEqualTo("\"http\"");
    }
}
