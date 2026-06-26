/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.xml.ResourceEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceEntityResolverTest {
    @Test
    void resolveEntityLoadsSiblingPackageResourceFromSystemIdFileName() throws Exception {
        ResourceEntityResolver resolver = new ResourceEntityResolver(ResourceEntityResolverTest.class);

        InputSource inputSource = resolver.resolveEntity(null, "https://example.test/dtd/test-entity.dtd");

        assertThat(inputSource).isNotNull();
        try (InputStream stream = inputSource.getByteStream()) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("<!ELEMENT sample (#PCDATA)>");
        }
    }
}
