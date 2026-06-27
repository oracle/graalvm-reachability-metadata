/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_attoparser.attoparser;

import org.attoparser.AttoParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassLoaderUtilsTest {

    @Test
    void loadsVersionPropertiesWhenContextClassLoaderCannotFindThem() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingResourceClassLoader = new ClassLoader(null) {
        };

        Thread.currentThread().setContextClassLoader(missingResourceClassLoader);
        try {
            assertThat(AttoParser.VERSION).isNotBlank();
            assertThat(AttoParser.BUILD_TIMESTAMP).isNotBlank();
            assertThat(AttoParser.VERSION_MAJOR).isPositive();
            assertThat(AttoParser.VERSION_MINOR).isGreaterThanOrEqualTo(0);
            assertThat(AttoParser.VERSION_BUILD).isGreaterThanOrEqualTo(0);
            assertThat(AttoParser.VERSION_TYPE).isNotBlank();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
