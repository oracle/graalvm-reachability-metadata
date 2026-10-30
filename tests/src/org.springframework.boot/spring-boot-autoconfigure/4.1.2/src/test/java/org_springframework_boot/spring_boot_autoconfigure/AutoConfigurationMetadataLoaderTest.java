/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoConfigurationMetadataLoaderTest {
    @Test
    void filtersCandidatesUsingClasspathMetadata() {
        assertCandidateFiltering(getClass().getClassLoader());
    }

    @Test
    void filtersCandidatesUsingSystemMetadata() {
        assertCandidateFiltering(null);
    }

    private void assertCandidateFiltering(ClassLoader classLoader) {
        AutoConfigurationImportSelector selector = AutoConfigurationTestSupport.selector(classLoader);
        assertFalse(selector.getExclusionFilter().test(
                "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"));
        assertTrue(selector.getExclusionFilter().test(
                "org.springframework.boot.autoconfigure.task.TaskExecutorConfigurations$TaskExecutorContextPropagationConfiguration"));
    }
}
