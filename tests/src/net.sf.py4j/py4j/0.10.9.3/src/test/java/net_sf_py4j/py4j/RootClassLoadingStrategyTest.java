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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class RootClassLoadingStrategyTest {
    private final RootClassLoadingStrategy loadingStrategy = new RootClassLoadingStrategy();

    @Test
    void loadsClassesByFullyQualifiedName() throws ClassNotFoundException {
        Class<?> loadedClass = loadingStrategy.classForName(String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    void reportsMissingClasses() {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> loadingStrategy.classForName("example.missing.Py4JClass"));
    }

    @Test
    void exposesRootClassLoader() {
        assertThat(loadingStrategy.getClassLoader()).isSameAs(RootClassLoadingStrategy.class.getClassLoader());
    }
}
