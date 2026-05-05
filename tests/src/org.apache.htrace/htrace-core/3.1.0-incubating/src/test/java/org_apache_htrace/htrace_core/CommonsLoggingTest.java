/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.commons.logging.LogFactory;
import org.apache.htrace.commons.logging.impl.LogFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsLoggingTest {

    private final PrintStream systemErr = System.err;

    private ByteArrayOutputStream outputStreamCaptor;

    @BeforeEach
    void setUp() {
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStreamCaptor));
        LogFactory.releaseAll();
    }

    @AfterEach
    void tearDown() {
        LogFactory.releaseAll();
        System.clearProperty(LogFactoryImpl.LOG_PROPERTY);
        System.setErr(systemErr);
    }

    @Test
    void usesSimpleLogViaLogFactoryDiscovery() {
        System.setProperty(LogFactoryImpl.LOG_PROPERTY, "org.apache.htrace.commons.logging.impl.SimpleLog");

        LogFactory.getLog("SimpleLogTest").info("info message");

        assertThat(outputStreamCaptor.toString()).isEqualTo("[INFO] SimpleLogTest - info message\n");
    }

    @Test
    void usesJdk14LoggerViaLogFactoryDiscovery() {
        System.setProperty(LogFactoryImpl.LOG_PROPERTY, "org.apache.htrace.commons.logging.impl.Jdk14Logger");

        LogFactory.getLog("Jdk14LoggerTest").error("error message");

        assertThat(outputStreamCaptor.toString()).contains("SEVERE: error message");
    }
}
