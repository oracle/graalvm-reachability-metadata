/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParseableInnerParseableResourcesTest {
    private static final String RESOURCE_NAME = "parseable-inner-parseable-resources.conf";

    @Test
    void parsesClasspathResourceByEnumeratingResourcesFromClassLoader() {
        ClassLoader classLoader = ParseableInnerParseableResourcesTest.class.getClassLoader();

        Config config = ConfigFactory.parseResources(classLoader, RESOURCE_NAME);

        assertThat(config.getString("parseableResources.service")).isEqualTo("classpath");
        assertThat(config.getInt("parseableResources.timeout")).isEqualTo(42);
        assertThat(config.getBoolean("parseableResources.enabled")).isTrue();
    }
}
