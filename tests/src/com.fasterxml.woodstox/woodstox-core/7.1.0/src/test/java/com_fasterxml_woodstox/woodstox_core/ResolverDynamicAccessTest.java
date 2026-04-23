/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.scanner.dtd.Resolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class ResolverDynamicAccessTest {
    @Test
    void resolvesCatalogResourcesWithAndWithoutAnExplicitClassLoader() throws Exception {
        Resolver resolver = new Resolver();
        resolver.registerCatalogEntry("urn:test:system", "resolver/test.dtd", null);
        resolver.registerCatalogEntry(
                "urn:test:loader",
                "resolver/test.dtd",
                ResolverDynamicAccessTest.class.getClassLoader());

        InputSource systemMapped = resolver.resolveEntity("urn:test:system", null);
        InputSource loaderMapped = resolver.resolveEntity("urn:test:loader", null);

        assertThat(systemMapped).isNotNull();
        assertThat(systemMapped.getCharacterStream()).isNotNull();
        assertThat(loaderMapped).isNotNull();
        assertThat(loaderMapped.getCharacterStream()).isNotNull();
    }
}
