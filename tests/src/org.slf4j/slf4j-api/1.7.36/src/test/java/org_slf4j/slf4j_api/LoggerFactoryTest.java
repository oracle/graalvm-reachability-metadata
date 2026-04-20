/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_api;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerFactoryTest {

    private static final ILoggerFactory INITIALIZED_FACTORY = LoggerFactory.getILoggerFactory();

    @Test
    void initializesAndProvidesUsableLoggersWithoutAnExternalBinding() {
        Logger classLogger = LoggerFactory.getLogger(LoggerFactoryTest.class);
        Logger namedLogger = LoggerFactory.getLogger("logger-factory-test");

        assertThat(INITIALIZED_FACTORY).isSameAs(LoggerFactory.getILoggerFactory());
        assertThat(classLogger).isNotNull();
        assertThat(namedLogger).isNotNull();
        assertThat(classLogger.getName()).isNotBlank();
        assertThat(namedLogger.getName()).isNotBlank();
    }
}
