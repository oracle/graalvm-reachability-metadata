/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

public class PullPolicyTest {
    @Test
    void instantiatesTheConfiguredPullPolicy() {
        ImagePullPolicy policy = PullPolicy.defaultPolicy();

        assertThat(policy).isInstanceOf(ConfiguredPolicy.class);
        assertThat(policy.shouldPull(DockerImageName.parse("nginx:1-alpine-slim"))).isFalse();
    }

    public static class ConfiguredPolicy implements ImagePullPolicy {
        @Override
        public boolean shouldPull(DockerImageName imageName) {
            return false;
        }
    }
}
