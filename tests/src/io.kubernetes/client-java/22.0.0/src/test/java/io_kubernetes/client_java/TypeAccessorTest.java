/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.TypeAccessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeAccessorTest {
    @Test
    void readsResourceApiVersionAndKind() throws Exception {
        V1Deployment deployment = new V1Deployment().apiVersion("apps/v1").kind("Deployment");

        assertThat(TypeAccessor.apiVersion(deployment)).isEqualTo("apps/v1");
        assertThat(TypeAccessor.kind(deployment)).isEqualTo("Deployment");
    }
}
