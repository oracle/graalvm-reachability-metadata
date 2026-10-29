/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_annotations;

import org.apache.maven.api.annotations.Config.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigSourceTest {
    @Test
    void enumeratesPropertySources() {
        assertArrayEquals(new Source[]{Source.SYSTEM_PROPERTIES, Source.USER_PROPERTIES, Source.MODEL}, Source.values());
    }

    @Test
    void resolvesPropertySourcesByName() {
        for (Source source : Source.values()) {
            assertSame(source, Source.valueOf(source.name()));
        }
        assertSame(Source.SYSTEM_PROPERTIES, Source.valueOf("SYSTEM_PROPERTIES"));
        assertSame(Source.USER_PROPERTIES, Source.valueOf("USER_PROPERTIES"));
        assertSame(Source.MODEL, Source.valueOf("MODEL"));
    }

    @Test
    void rejectsUnknownPropertySource() {
        assertThrows(IllegalArgumentException.class, () -> Source.valueOf("ENVIRONMENT"));
    }
}
