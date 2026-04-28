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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourcesTest {
    private static final String ASHKENAZIC_LANGUAGE_RULES =
            "com/github/dockerjava/zerodep/shaded/org/apache/commons/codec/language/bm/ash_lang.txt";

    @Test
    void opensShadedCodecResourceFromClassLoader() throws Exception {
        try (InputStream inputStream = Resources.getInputStream(ASHKENAZIC_LANGUAGE_RULES)) {
            String rules = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(rules).contains("ASHKENAZIC", "polish+russian+german+english");
        }
    }

    @Test
    void missingResourceReportsTheRequestedName() {
        String missingResource = "com/github/dockerjava/zerodep/shaded/org/apache/commons/codec/language/bm/missing-rules.txt";

        assertThatThrownBy(() -> Resources.getInputStream(missingResource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingResource);
    }
}
