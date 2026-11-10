/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ModelMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelMapperTest {
    @Test
    void discoversBuiltInModelsForCoreAndGroupedResources() {
        assertThat(ModelMapper.getApiTypeClass("v1", "Pod")).isEqualTo(V1Pod.class);
        assertThat(ModelMapper.getApiTypeClass("v1", "PodList")).isEqualTo(V1PodList.class);
        assertThat(ModelMapper.getApiTypeClass("apps/v1", "Deployment")).isEqualTo(V1Deployment.class);
        assertThat(ModelMapper.preBuiltGetGroupVersionKindByClass(V1Pod.class)).hasValueSatisfying(gvk -> {
            assertThat(gvk.getGroup()).isEmpty();
            assertThat(gvk.getVersion()).isEqualTo("v1");
            assertThat(gvk.getKind()).isEqualTo("Pod");
        });
    }
}
