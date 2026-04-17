/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.loader.EnhancedServiceNotFoundException;
import org.apache.seata.common.loader.LoadLevel;
import org.apache.seata.discovery.loadbalance.LoadBalance;
import org.apache.seata.discovery.loadbalance.LoadBalanceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EnhancedServiceLoaderInnerEnhancedServiceLoaderTest {
    @AfterEach
    void unloadServices() {
        EnhancedServiceLoader.unloadAll();
    }

    @Test
    void loadWithNullLoaderFallsBackToSystemResources() {
        assertThatThrownBy(() -> EnhancedServiceLoader.load(
                LoadBalance.class,
                LoadBalanceFactory.ROUND_ROBIN_LOAD_BALANCE + "-missing",
                (ClassLoader) null))
                .isInstanceOf(EnhancedServiceNotFoundException.class)
                .hasMessageContaining(LoadBalance.class.getName());
    }

    @Test
    void loadUsesDefaultConstructorForTestExtension() {
        TestService service = EnhancedServiceLoader.load(
                TestService.class,
                "default",
                EnhancedServiceLoaderInnerEnhancedServiceLoaderTest.class.getClassLoader());

        assertThat(service.describe()).isEqualTo("default-constructor");
    }

    @Test
    void loadUsesDeclaredConstructorArgumentsForTestExtension() {
        TestService service = EnhancedServiceLoader.load(
                TestService.class,
                "argument",
                new Class[]{String.class},
                new Object[]{"configured-value"});

        assertThat(service.describe()).isEqualTo("configured-value");
    }

    public interface TestService {
        String describe();
    }

    @LoadLevel(name = "default")
    public static final class DefaultTestService implements TestService {
        @Override
        public String describe() {
            return "default-constructor";
        }
    }

    @LoadLevel(name = "argument")
    public static final class ParameterizedTestService implements TestService {
        private final String value;

        public ParameterizedTestService(String value) {
            this.value = value;
        }

        @Override
        public String describe() {
            return value;
        }
    }
}
