/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.log;

import java.util.Properties;

import com.mchange.v2.cfg.MultiPropertiesConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MLogTest {
    private static final String LOGGER_PREFIX = "transformed.";
    private static final String DEFAULT_LOGGER_NAME = LOGGER_PREFIX + "global";

    @AfterEach
    void restoreDefaultLoggingConfiguration() {
        MLog.refreshConfig(null, null);
    }

    @Test
    void refreshConfigInstantiatesConfiguredNameTransformer() {
        MLog.refreshConfig(
            new MultiPropertiesConfig[] {
                overrideConfig(
                    "com.mchange.v2.log.MLog",
                    "jdk14",
                    "com.mchange.v2.log.NameTransformer",
                    PrefixingNameTransformer.class.getName()
                )
            },
            "JUnit test override"
        );

        MLogger namedLogger = MLog.getLogger("named.logger");
        MLogger classLogger = MLog.getLogger(MLogTest.class);
        MLogger defaultLogger = MLog.getLogger();

        assertThat(namedLogger.getName()).isEqualTo(LOGGER_PREFIX + "named.logger");
        assertThat(classLogger.getName()).isEqualTo(LOGGER_PREFIX + MLogTest.class.getName());
        assertThat(defaultLogger.getName()).isEqualTo(DEFAULT_LOGGER_NAME);
    }

    private static MultiPropertiesConfig overrideConfig(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return MultiPropertiesConfig.fromProperties(properties);
    }

    public static final class PrefixingNameTransformer implements NameTransformer {
        @Override
        public String transformName(String name) {
            return LOGGER_PREFIX + name;
        }

        @Override
        public String transformName(Class cl) {
            return LOGGER_PREFIX + cl.getName();
        }

        @Override
        public String transformName() {
            return DEFAULT_LOGGER_NAME;
        }
    }
}
