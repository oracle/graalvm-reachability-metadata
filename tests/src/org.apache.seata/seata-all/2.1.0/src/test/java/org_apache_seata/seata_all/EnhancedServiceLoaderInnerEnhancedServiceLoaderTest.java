/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.loader.EnhancedServiceNotFoundException;
import org.apache.seata.common.loader.LoadLevel;
import org.apache.seata.common.loader.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnhancedServiceLoaderInnerEnhancedServiceLoaderTest {
    @BeforeEach
    void resetLoaderCache() {
        EnhancedServiceLoader.unload(ExtensionService.class);
        EnhancedServiceLoader.unload(SystemResourceOnlyService.class);
    }

    @AfterEach
    void clearLoaderCache() {
        EnhancedServiceLoader.unload(ExtensionService.class);
        EnhancedServiceLoader.unload(SystemResourceOnlyService.class);
    }

    @Test
    void loadByNameUsesServiceResourceAndDefaultConstructor() {
        ClassLoader testClassLoader = EnhancedServiceLoaderInnerEnhancedServiceLoaderTest.class.getClassLoader();

        ExtensionService extension = EnhancedServiceLoader.load(ExtensionService.class, "default", testClassLoader);

        assertThat(extension.describe()).isEqualTo("default-extension");
    }

    @Test
    void loadByNameWithExplicitArgumentTypesUsesMatchingConstructor() {
        ExtensionService extension = EnhancedServiceLoader.load(
                ExtensionService.class,
                "argument",
                new Class<?>[] {String.class, int.class},
                new Object[] {"branch", 7});

        assertThat(extension.describe()).isEqualTo("branch-7");
    }

    @Test
    void nullClassLoaderFallsBackToSystemResourceLookup() {
        assertThatThrownBy(() -> EnhancedServiceLoader.load(SystemResourceOnlyService.class, (ClassLoader) null))
                .isInstanceOf(EnhancedServiceNotFoundException.class)
                .hasMessageContaining(SystemResourceOnlyService.class.getName());
    }

    public interface ExtensionService {
        String describe();
    }

    public interface SystemResourceOnlyService {
    }

    @LoadLevel(name = "default", order = 20, scope = Scope.PROTOTYPE)
    public static class DefaultExtension implements ExtensionService {
        public DefaultExtension() {
        }

        @Override
        public String describe() {
            return "default-extension";
        }
    }

    @LoadLevel(name = "argument", order = 10, scope = Scope.PROTOTYPE)
    public static class ArgumentExtension implements ExtensionService {
        private final String name;
        private final int value;

        public ArgumentExtension(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String describe() {
            return name + "-" + value;
        }
    }
}
