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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Log4jTest {

    private ByteArrayOutputStream stdOut;
    private final PrintStream systemOut = System.out;
    private static final String baseDir = "./logs/";
    private static final String[] logs = new String[]{"dailyRollingAppender.log", "fileAppender.log", "rollingFileAppender.log"};

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
        assertThat(stdOut.toString(StandardCharsets.UTF_8)).contains("info message");
        assertFileContent();
    }

    @Test
    void consoleInfoLoggerProperties() {
        PropertyConfigurator.configure(this.getClass().getClassLoader().getResource("log4j.properties"));
        final Logger logger = LogManager.getLogger(this.getClass());
        logger.info("info message");
        assertThat(stdOut.toString(StandardCharsets.UTF_8)).contains("info message");
        assertFileContent();
    }

    private void assertFileContent() {
        for (String path: logs) {
            final File file = new File(baseDir + path);
            assertThat(file.exists()).isTrue();
            try {
                final List<String> content = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                assertThat(content.size()).isGreaterThan(0);
                assertThat(content.get(0).contains("info message")).isTrue();
            } catch (IOException e) {
                Assertions.fail("read file error", e);
            }
        }
    }

    @AfterEach
    public void tearDown() {
        System.setOut(systemOut);
        final File logFileDir = new File(baseDir);
        final File[] files = logFileDir.listFiles();
        if (files != null) {
            for (File file: files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        logFileDir.delete();
    }
}
