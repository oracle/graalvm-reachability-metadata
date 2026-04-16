/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoggerFactoryTest {

    @AfterEach
    void resetLoggerFactoryState() {
        LoggerFactory.reset();
        setSyntheticLoggerFactoryClass(null);
    }

    @Test
    void initializesViaLoggerFactoryClassLookup() {
        Logger logger = LoggerFactory.getLogger(LoggerFactoryTest.class);

        assertThat(logger).isNotNull();
        assertThat(LoggerFactory.INITIALIZATION_STATE).isEqualTo(LoggerFactory.NOP_FALLBACK_INITILIZATION);
    }

    @Test
    void fallsBackToSystemResourceLookupForBootstrapLoadedClass() {
        assertThat(Locale.class.getClassLoader()).isNull();
        setSyntheticLoggerFactoryClass(Locale.class);

        Logger logger = LoggerFactory.getLogger("bootstrap-loader");

        assertThat(logger).isNotNull();
        assertThat(LoggerFactory.INITIALIZATION_STATE).isEqualTo(LoggerFactory.NOP_FALLBACK_INITILIZATION);
    }

    private static void setSyntheticLoggerFactoryClass(Class<?> clazz) {
        try {
            Field field = LoggerFactory.class.getDeclaredField("class$org$slf4j$LoggerFactory");
            field.setAccessible(true);
            field.set(null, clazz);
        } catch (ReflectiveOperationException e) {
            fail("Expected synthetic LoggerFactory class cache field to exist", e);
        }
    }
}
