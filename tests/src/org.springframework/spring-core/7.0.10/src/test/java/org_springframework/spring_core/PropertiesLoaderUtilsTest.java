/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** Verifies loading all occurrences of a classpath properties resource. */
public class PropertiesLoaderUtilsTest {
    @Test
    void loadsPropertiesThroughProvidedClassLoader() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadAllProperties(
                "org_springframework/spring_core/dynamic-access.properties", getClass().getClassLoader());

        assertThat(properties).containsEntry("library", "spring-core");
    }
}
