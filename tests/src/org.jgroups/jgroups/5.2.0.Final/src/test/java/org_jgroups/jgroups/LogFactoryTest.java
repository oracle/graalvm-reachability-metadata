/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.logging.JDKLogImpl;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    private static final String LOG_CLASS_PROPERTY = "jgroups.log_class";
    private static final String CONFIGURED_LOG_CLASS = "org_jgroups.jgroups.LogFactoryTest$ConfiguredLog";

    static {
        configureJGroupsDefaultsForAvailabilityLookup();
    }

    @BeforeAll
    static void configureDefaults() {
        configureJGroupsDefaultsForAvailabilityLookup();
    }

    @Test
    void createsDefaultLogAfterCheckingAvailableLoggingImplementations() {
        Log log = LogFactory.getLog(LogFactoryTest.class);

        assertThat(ConfiguredLog.class.getName()).isEqualTo(CONFIGURED_LOG_CLASS);
        assertThat(System.getProperty(LOG_CLASS_PROPERTY)).isNull();
        assertThat(log).isNotNull();
    }

    private static void configureJGroupsDefaultsForAvailabilityLookup() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.clearProperty("jgroups.use.jdk_logger");
        System.clearProperty(LOG_CLASS_PROPERTY);
    }

    public static final class ConfiguredLog extends JDKLogImpl {
        public ConfiguredLog(Class<?> clazz) {
            super(clazz);
        }
    }
}
