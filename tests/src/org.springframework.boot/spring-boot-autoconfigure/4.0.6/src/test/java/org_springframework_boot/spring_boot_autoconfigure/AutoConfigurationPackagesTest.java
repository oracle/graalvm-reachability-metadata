/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoConfigurationPackagesTest {
    @Test
    void importsBasePackagesForEntityAndRepositoryDiscovery() {
        new ApplicationContextRunner().withUserConfiguration(Packages.class).run(context -> {
            assertTrue(AutoConfigurationPackages.has(context.getBeanFactory()));
            assertEquals(List.of("example.orders", "example.inventory"),
                    AutoConfigurationPackages.get(context.getBeanFactory()));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = {"example.orders", "example.inventory"})
    public static class Packages {
    }
}
