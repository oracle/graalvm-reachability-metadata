/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class Log4jTest {

    private ByteArrayOutputStream stdOut;

    @BeforeEach
    public void setUp() {
        stdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdOut));
    }

    @Test
    void consoleInfoLogger() {
        DOMConfigurator.configure(this.getClass().getClassLoader().getResource("log4j.xml"));
        final Logger logger = LogManager.getLogger(this.getClass());
        logger.info("info message");
        assertThat(stdOut.toString()).contains("info message");
    }

    @Test
    void consoleInfoLoggerProperties() {
        PropertyConfigurator.configure(this.getClass().getClassLoader().getResource("log4j.properties"));
        final Logger logger = LogManager.getLogger(this.getClass());
        logger.info("info message");
        assertThat(stdOut.toString()).contains("info message");
    }
}
