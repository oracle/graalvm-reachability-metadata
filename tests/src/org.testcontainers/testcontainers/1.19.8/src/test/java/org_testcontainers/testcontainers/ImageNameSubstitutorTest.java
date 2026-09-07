/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

import static org.assertj.core.api.Assertions.assertThat;

public class ImageNameSubstitutorTest {
    @Test
    void instantiatesAndAppliesTheConfiguredSubstitutor() {
        DockerImageName imageName = DockerImageName.parse("nginx:1-alpine-slim");

        assertThat(ImageNameSubstitutor.instance().apply(imageName)).isEqualTo(imageName);
    }

    public static class ConfiguredSubstitutor extends ImageNameSubstitutor {
        @Override
        public DockerImageName apply(DockerImageName original) {
            return original;
        }

        @Override
        protected String getDescription() {
            return "test substitutor";
        }
    }
}
