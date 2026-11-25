/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnableAutoConfigurationTest {
    @Test
    void explicitExclusionRemovesOnlyTheSelectedAutoConfiguration() {
        AutoConfigurationImportSelector selector = AutoConfigurationTestSupport.selector(getClass().getClassLoader());
        List<String> imports = List.of(selector.selectImports(AnnotationMetadata.introspect(Application.class)));
        assertTrue(imports.contains(TaskExecutionAutoConfiguration.class.getName()));
        assertFalse(imports.contains(TaskSchedulingAutoConfiguration.class.getName()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = TaskSchedulingAutoConfiguration.class)
    public static class Application {
    }
}
