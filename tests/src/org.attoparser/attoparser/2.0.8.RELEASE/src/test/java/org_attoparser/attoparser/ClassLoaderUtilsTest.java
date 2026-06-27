/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_attoparser.attoparser;

import org.attoparser.AttoParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassLoaderUtilsTest {

    @Test
    void reportsMalformedVersionPropertiesWhenContextClassLoaderCannotFindThem() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingResourceClassLoader = new ClassLoader(null) {
        };

        Thread.currentThread().setContextClassLoader(missingResourceClassLoader);
        try {
            assertThatThrownBy(() -> AttoParser.isVersionStableRelease())
                    .isInstanceOf(ExceptionInInitializerError.class)
                    .hasMessageContaining("Identified AttoParser version is '${pom.version}'");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
