/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.Resources;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {
    @Test
    void opensBundledCodecLanguageResource() throws Exception {
        final String resourceName = Resources.class.getName().replace('.', '/')
                .replace("Resources", "language/dmrules.txt");

        try (InputStream inputStream = Resources.getInputStream(resourceName)) {
            final String rules = new String(inputStream.readNBytes(256), StandardCharsets.UTF_8);

            assertThat(rules).contains("Licensed to the Apache Software Foundation");
        }
    }
}
