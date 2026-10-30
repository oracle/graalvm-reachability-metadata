/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionLoaderTest {

    @Test
    void discoversComponentsFromAPackageSource() {
        PackageApplication application = new PackageApplication();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            application.loadPackage(context, "org_springframework_boot.spring_boot.packagesource");
            context.refresh();

            assertThat(context.containsBean("packageGreeting")).isTrue();
            assertThat(context.getBean("packageGreeting").toString()).isEqualTo("Hello from the package source");
        }
    }

    public static class PackageApplication extends SpringApplication {

        public void loadPackage(GenericApplicationContext context, String packageName) {
            super.load(context, new Object[] {packageName});
        }
    }
}
