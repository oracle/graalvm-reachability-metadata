/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.lazy.LazyFeatureDependencies;
import org.mongodb.morphia.mapping.lazy.LazyProxyFactory;

public class LazyFeatureDependenciesTest {
    @Test
    void createsDefaultProxyFactoryWhenLazyDependenciesAreAvailable() {
        assertThat(LazyFeatureDependencies.testDependencyFullFilled()).isTrue();

        LazyProxyFactory factory = LazyFeatureDependencies.createDefaultProxyFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getName()).isEqualTo("org.mongodb.morphia.mapping.lazy.CGLibLazyProxyFactory");
    }
}
