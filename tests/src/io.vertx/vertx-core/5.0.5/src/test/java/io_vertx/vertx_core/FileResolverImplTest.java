/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.file.impl.FileResolverImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileResolverImplTest {

    @Test
    void resolveFileCopiesClasspathResourceToCache() throws Exception {
        FileResolverImpl resolver = new FileResolverImpl();
        try {
            File resolved = resolver.resolve("file-resolver-valid-classpath-resource.txt");

            assertTrue(resolved.isFile());
            assertEquals("resolved from the test classpath", Files.readString(resolved.toPath(), StandardCharsets.UTF_8));
        } finally {
            resolver.close();
        }
    }
}
