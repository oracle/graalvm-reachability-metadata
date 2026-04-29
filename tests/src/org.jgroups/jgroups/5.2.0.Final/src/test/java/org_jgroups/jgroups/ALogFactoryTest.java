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

public class ALogFactoryTest {
    private static final String LOG_CLASS_PROPERTY = "jgroups.log_class";

    @BeforeAll
    public static void configureLogFactory() {
        System.setProperty(LOG_CLASS_PROPERTY, JDKLogImpl.class.getName());
    }

    @Test
    void createsConfiguredLoggerUsingPublicFactory() {
        Log log = LogFactory.getLog(ALogFactoryTest.class);

        assertThat(log).isInstanceOf(JDKLogImpl.class);
        assertThat(LogFactory.loggerType()).isEqualTo("JDKLogImpl");

        log.info("configured JGroups logger is usable");
    }
}
