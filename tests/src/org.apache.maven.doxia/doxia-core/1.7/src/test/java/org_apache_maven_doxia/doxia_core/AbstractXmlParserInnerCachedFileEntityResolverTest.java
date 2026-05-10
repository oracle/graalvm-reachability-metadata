/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_core;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.maven.doxia.parser.AbstractXmlParser.CachedFileEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractXmlParserInnerCachedFileEntityResolverTest {
    private static final String RESOURCE_NAME = "doxia-cached-file-entity-resolver-test.dtd";
    private static final String SYSTEM_ID = "file:/not-present/" + RESOURCE_NAME;

    @Test
    void resolvesFileEntityFromClasspathResourceBeforeCachingTempFile() throws Exception {
        ExposedCachedFileEntityResolver.clearEntityCache();
        File cachedTempFile = new File(System.getProperty("java.io.tmpdir"), RESOURCE_NAME);
        Files.deleteIfExists(cachedTempFile.toPath());

        try {
            CachedFileEntityResolver resolver = new ExposedCachedFileEntityResolver();
            InputSource source = resolver.resolveEntity("public-test-id", SYSTEM_ID);

            assertThat(source.getPublicId()).isEqualTo("public-test-id");
            assertThat(source.getSystemId()).isEqualTo(SYSTEM_ID);
            try (InputStream stream = source.getByteStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .contains("cachedFileResolverEntity")
                        .contains("resolved from classpath resource");
            }
            assertThat(cachedTempFile).exists();
        } finally {
            ExposedCachedFileEntityResolver.clearEntityCache();
            Files.deleteIfExists(cachedTempFile.toPath());
        }
    }

    private static final class ExposedCachedFileEntityResolver extends CachedFileEntityResolver {
        static void clearEntityCache() {
            ENTITY_CACHE.clear();
        }
    }
}
