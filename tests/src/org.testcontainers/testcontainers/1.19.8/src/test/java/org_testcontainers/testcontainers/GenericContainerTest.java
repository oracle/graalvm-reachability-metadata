/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericContainerTest {
    @Test
    void determinesWhetherASubclassSupportsContainerReuse() {
        ReusableContainer container = new ReusableContainer();
        ContainerWithCreationCallback customizedContainer = new ContainerWithCreationCallback();

        assertThat(container.supportsReuse()).isTrue();
        assertThat(customizedContainer.supportsReuse()).isFalse();
    }

    public static class ReusableContainer extends GenericContainer<ReusableContainer> {
        public ReusableContainer() {
            super(DockerImageName.parse("nginx:1-alpine-slim"));
        }

        public boolean supportsReuse() {
            return canBeReused();
        }
    }

    public static class ContainerWithCreationCallback extends GenericContainer<ContainerWithCreationCallback> {
        public ContainerWithCreationCallback() {
            super(DockerImageName.parse("nginx:1-alpine-slim"));
        }

        public boolean supportsReuse() {
            return canBeReused();
        }

        @Override
        protected void containerIsCreated(String containerId) {}
    }
}
