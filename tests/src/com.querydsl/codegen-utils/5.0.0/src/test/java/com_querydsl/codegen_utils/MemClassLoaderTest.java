/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.MemClassLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MemClassLoaderTest {

    @Test
    void enumeratesAndReadsParentResources() throws IOException {
        MemClassLoader loader = new MemClassLoader(
                MemClassLoaderTest.class.getClassLoader(), Collections.emptyMap());

        List<URL> resources = Collections.list(loader.getResources("codegen-utils/message.txt"));

        assertThat(resources).hasSize(1);
        try (InputStream input = resources.get(0).openStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("Code generation resource\n");
        }
    }
}
