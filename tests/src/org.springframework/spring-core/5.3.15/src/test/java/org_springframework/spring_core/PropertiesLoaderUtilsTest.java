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

/** Verifies aggregation of properties from classpath resources. */
public class PropertiesLoaderUtilsTest {
    @Test
    void loadsAllClasspathProperties() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadAllProperties(
                "spring-core-coverage.properties", getClass().getClassLoader());

        assertThat(properties).containsEntry("feature", "spring-core");
    }
}
