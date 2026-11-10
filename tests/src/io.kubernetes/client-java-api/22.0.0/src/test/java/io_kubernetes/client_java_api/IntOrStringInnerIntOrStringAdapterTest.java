/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1ServicePort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntOrStringInnerIntOrStringAdapterTest {
    @Test
    void selectsAnnotatedAdapterForProgrammaticallyCreatedServicePorts() throws Exception {
        V1ServicePort named = new V1ServicePort().port(80).targetPort(new IntOrString("http"));
        ModelJson.assertJsonEquals("""
                {"port":80,"targetPort":"http"}
                """, named.toJson());
        assertThat(V1ServicePort.fromJson(named.toJson()).getTargetPort().getStrValue()).isEqualTo("http");

        V1ServicePort numbered = new V1ServicePort().port(80).targetPort(new IntOrString(8080));
        ModelJson.assertJsonEquals("""
                {"port":80,"targetPort":8080}
                """, numbered.toJson());
        assertThat(V1ServicePort.fromJson(numbered.toJson()).getTargetPort().getIntValue()).isEqualTo(8080);
    }
}
