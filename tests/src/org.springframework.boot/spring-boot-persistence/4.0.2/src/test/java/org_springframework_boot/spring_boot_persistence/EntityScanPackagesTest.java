/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_persistence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityScanPackagesTest {

    @Test
    void unconfiguredBeanFactoryHasNoEntityPackages() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        assertThat(EntityScanPackages.get(factory).getPackageNames()).isEmpty();
    }

    @Test
    void registrationsCombineDistinctPackagesInInsertionOrder() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        EntityScanPackages.register(factory, "app.catalog", "app.orders", "app.catalog");
        EntityScanPackages.register(factory, List.of("app.orders", "app.customers"));

        EntityScanPackages packages = EntityScanPackages.get(factory);

        assertThat(packages.getPackageNames()).containsExactly("app.catalog", "app.orders", "app.customers");
        assertThat(EntityScanPackages.get(factory)).isSameAs(packages);
    }

    @Test
    void blankPackageNamesAreExcluded() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        EntityScanPackages.register(factory, "", "app.inventory", "   ");

        assertThat(EntityScanPackages.get(factory).getPackageNames()).containsExactly("app.inventory");
    }
}
