/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v1.xml;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceEntityResolverTest {
    @Test
    void resolveEntityLoadsResourceFromTheSiblingPackage() throws Exception {
        ResourceEntityResolver resolver = new ResourceEntityResolver(ResourceEntityResolverTest.class);

        InputSource inputSource = resolver.resolveEntity(
            null,
            "https://example.invalid/resource-entity-resolver-test.dtd"
        );

        assertThat(inputSource).isNotNull();
        try (InputStream stream = inputSource.getByteStream()) {
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void resolveEntityReturnsNullWhenTheResourceDoesNotExist() throws Exception {
        ResourceEntityResolver resolver = new ResourceEntityResolver(ResourceEntityResolverTest.class);

        InputSource inputSource = resolver.resolveEntity(
            null,
            "https://example.invalid/does-not-exist.dtd"
        );

        assertThat(inputSource).isNull();
    }
}
