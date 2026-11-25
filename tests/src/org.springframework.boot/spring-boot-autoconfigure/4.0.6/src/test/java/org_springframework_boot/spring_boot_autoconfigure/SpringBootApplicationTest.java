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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringBootApplicationTest {
    @Test
    void applicationExclusionIsAppliedThroughTheComposedAnnotation() {
        AutoConfigurationImportSelector selector = AutoConfigurationTestSupport.selector(getClass().getClassLoader());
        List<String> imports = List.of(selector.selectImports(AnnotationMetadata.introspect(Application.class)));
        assertFalse(imports.contains(TaskExecutionAutoConfiguration.class.getName()));
        assertTrue(imports.contains(TaskSchedulingAutoConfiguration.class.getName()));
    }

    @SpringBootApplication(proxyBeanMethods = false, exclude = TaskExecutionAutoConfiguration.class)
    public static class Application {
    }
}
