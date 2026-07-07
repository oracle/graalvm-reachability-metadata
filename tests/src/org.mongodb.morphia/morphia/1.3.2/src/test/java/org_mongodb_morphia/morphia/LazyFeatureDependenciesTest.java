/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.lazy.LazyFeatureDependencies;
import org.mongodb.morphia.mapping.lazy.LazyProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyFeatureDependenciesTest {
    @Test
    void createsDefaultProxyFactoryOnlyWhenLazyDependenciesAreAvailable() {
        boolean dependencyFullFilled = LazyFeatureDependencies.testDependencyFullFilled();

        LazyProxyFactory proxyFactory = LazyFeatureDependencies.createDefaultProxyFactory();

        if (dependencyFullFilled) {
            assertThat(proxyFactory).isNotNull();
            assertThat(proxyFactory.getClass().getName()).isEqualTo("org.mongodb.morphia.mapping.lazy.CGLibLazyProxyFactory");
        } else {
            assertThat(proxyFactory).isNull();
        }
    }
}
