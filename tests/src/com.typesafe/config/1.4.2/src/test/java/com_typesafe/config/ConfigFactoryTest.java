/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigLoadingStrategy;
import com.typesafe.config.ConfigParseOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigFactoryTest {
    private static final String STRATEGY_PROPERTY = "config.strategy";
    private static final String SERVICE_NAME = "custom-loader-service";

    private String originalStrategyProperty;

    @BeforeEach
    void saveStrategyProperty() {
        originalStrategyProperty = System.getProperty(STRATEGY_PROPERTY);
    }

    @AfterEach
    void restoreStrategyProperty() {
        if (originalStrategyProperty == null) {
            System.clearProperty(STRATEGY_PROPERTY);
        } else {
            System.setProperty(STRATEGY_PROPERTY, originalStrategyProperty);
        }
    }

    @Test
    void loadsDefaultApplicationWithConfiguredStrategyClass() {
        System.setProperty(STRATEGY_PROPERTY, TestConfigLoadingStrategy.class.getName());

        Config config = ConfigFactory.defaultApplication();

        assertThat(config.getString("service.name")).isEqualTo(SERVICE_NAME);
        assertThat(config.getBoolean("service.enabled")).isTrue();
    }

    public static class TestConfigLoadingStrategy implements ConfigLoadingStrategy {
        @Override
        public Config parseApplicationConfig(ConfigParseOptions parseOptions) {
            return ConfigFactory.parseString("""
                    service {
                      name = "custom-loader-service"
                      enabled = true
                    }
                    """);
        }
    }
}
