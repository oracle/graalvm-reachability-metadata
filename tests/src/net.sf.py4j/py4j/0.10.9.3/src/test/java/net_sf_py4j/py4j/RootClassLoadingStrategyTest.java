/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import org.junit.jupiter.api.Test;

import py4j.reflection.RootClassLoadingStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class RootClassLoadingStrategyTest {
    @Test
    void loadsClassByFullyQualifiedName() throws ClassNotFoundException {
        RootClassLoadingStrategy strategy = new RootClassLoadingStrategy();

        Class<?> resolvedClass = strategy.classForName(RootClassLoadingStrategyTest.class.getName());

        assertThat(resolvedClass).isSameAs(RootClassLoadingStrategyTest.class);
    }

    @Test
    void exposesRootClassLoader() {
        RootClassLoadingStrategy strategy = new RootClassLoadingStrategy();

        assertThat(strategy.getClassLoader()).isSameAs(RootClassLoadingStrategy.class.getClassLoader());
    }
}
