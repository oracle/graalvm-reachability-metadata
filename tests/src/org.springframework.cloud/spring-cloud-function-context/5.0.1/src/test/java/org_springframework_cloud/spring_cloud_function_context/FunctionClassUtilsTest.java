/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.cloud.function.utils.FunctionClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootConfiguration
public class FunctionClassUtilsTest {
    @Test
    void getStartClassDiscoversBootConfigurationFromManifestResources() {
        String previousMainClass = System.getProperty("MAIN_CLASS");
        System.clearProperty("MAIN_CLASS");
        try {
            Class<?> startClass = FunctionClassUtils.getStartClass();

            assertThat(startClass).isEqualTo(FunctionClassUtilsTest.class);
        }
        finally {
            if (previousMainClass != null) {
                System.setProperty("MAIN_CLASS", previousMainClass);
            }
        }
    }
}
