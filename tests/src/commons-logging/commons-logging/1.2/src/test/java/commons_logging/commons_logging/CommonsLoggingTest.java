/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_logging.commons_logging;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.LogFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsLoggingTest {

    private final PrintStream systemErr = System.err;

    private ByteArrayOutputStream outputStreamCaptor;

    @BeforeEach
    public void setUp() {
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() {
        System.setErr(systemErr);
    }

    @Test
    void testSimpleLog() throws Exception {
        System.setProperty(LogFactoryImpl.LOG_PROPERTY, "org.apache.commons.logging.impl.SimpleLog");
        try {
            LogFactory.getLog("SimpleLogTest").info("info message");
            assertThat(outputStreamCaptor.toString()).isEqualTo("[INFO] SimpleLogTest - info message\n");
        } finally {
            System.clearProperty(LogFactoryImpl.LOG_PROPERTY);
            resetLogConstructorField();
        }
    }

    @Test
    void testJdk14Logger() throws Exception {
        System.setProperty(LogFactoryImpl.LOG_PROPERTY, "org.apache.commons.logging.impl.Jdk14Logger");
        try {
            LogFactory.getLog("Jdk14LoggerTest").error("error message");
            assertThat(outputStreamCaptor.toString()).contains("SEVERE: error message");
        } finally {
            System.clearProperty(LogFactoryImpl.LOG_PROPERTY);
            resetLogConstructorField();
        }
    }

    private void resetLogConstructorField() throws NoSuchFieldException, IllegalAccessException {
        Field field = LogFactoryImpl.class.getDeclaredField("logConstructor");
        field.setAccessible(true);
        field.set(LogFactory.getFactory(), null);
    }
}
