/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org_springframework_boot.spring_boot_autoconfigure.scan.UserConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AutoConfigurationExcludeFilterTest {
    @Test
    void constructsTheConfiguredExclusionFilterAndKeepsAutoConfigurationsOutOfComponentScanning() {
        AutoConfigurationImportSelector selector = AutoConfigurationTestSupport.selector(getClass().getClassLoader());
        assertFalse(selector.getExclusionFilter().test(
                "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ScanConfiguration.class);
            context.refresh();
            assertEquals("user configuration", context.getBean("scannedUserValue"));
            assertFalse(context.containsBean("accidentallyScannedAutoConfiguration"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = UserConfiguration.class,
            excludeFilters = @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
    public static class ScanConfiguration {
    }
}
