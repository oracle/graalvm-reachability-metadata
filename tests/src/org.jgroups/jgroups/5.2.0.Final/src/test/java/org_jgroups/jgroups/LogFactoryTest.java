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
        configureJGroupsDefaults();
    }

    @BeforeAll
    static void configureDefaults() {
        configureJGroupsDefaults();
    }

    @Test
    void createsLogUsingConfiguredLogClass() {
        Log log = LogFactory.getLog(LogFactoryTest.class);

        assertThat(System.getProperty(LOG_CLASS_PROPERTY)).isEqualTo(CONFIGURED_LOG_CLASS);
        assertThat(LogFactory.loggerType()).isEqualTo("ConfiguredLog");
        assertThat(log).isInstanceOf(ConfiguredLog.class);
    }

    private static void configureJGroupsDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty(LOG_CLASS_PROPERTY, CONFIGURED_LOG_CLASS);
    }

    public static final class ConfiguredLog extends JDKLogImpl {
        public ConfiguredLog(Class<?> clazz) {
            super(clazz);
        }
    }
}
