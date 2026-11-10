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
import io.kubernetes.client.util.Yaml;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelMapperTest {
    @Test
    void resolvesRegisteredModelsForCoreAndGroupedResources() {
        // Automatic discovery scans JARs or filesystem directories, not Native Image resources.
        ModelMapper.addModelMap("", "v1", "Pod", "pods", V1Pod.class, V1PodList.class);
        ModelMapper.addModelMap("apps", "v1", "Deployment", "deployments", V1Deployment.class, null);

        assertThat(ModelMapper.getApiTypeClass("v1", "Pod")).isEqualTo(V1Pod.class);
        assertThat(ModelMapper.getApiTypeClass("v1", "PodList")).isEqualTo(V1PodList.class);
        assertThat(ModelMapper.getApiTypeClass("apps/v1", "Deployment")).isEqualTo(V1Deployment.class);
        assertThat(ModelMapper.getGroupVersionKindByClass(V1Pod.class).getGroup()).isEmpty();
        assertThat(ModelMapper.getGroupVersionKindByClass(V1Pod.class).getVersion()).isEqualTo("v1");
        assertThat(ModelMapper.getGroupVersionKindByClass(V1Pod.class).getKind()).isEqualTo("Pod");

        V1Pod pod = new V1Pod().apiVersion("v1").kind("Pod");
        assertThat(Yaml.dump(pod)).contains("apiVersion: v1", "kind: Pod");
    }
}
