/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.Resources;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {
    @Test
    void opensResourceFromClassLoader() throws Exception {
        try (InputStream inputStream = Resources.getInputStream("docker-java-transport-zerodep-resource.txt")) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(content).isEqualTo("resource loaded by commons-codec Resources\n");
        }
    }
}
