/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import org.graalvm.logback.util.AppenderTags;
import org.graalvm.logback.util.LayoutTags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LogbackTests {

  private static final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

  private static final Map<String, String> layoutTagMap = new HashMap<>();
  static {
    layoutTagMap.put("patternLayout", LayoutTags.PATTERN_TAG);
    layoutTagMap.put("xmlLayout", LayoutTags.XML_TAG);
    layoutTagMap.put("htmlLayout", LayoutTags.HTML_TAG);
  }

  private static final Map<String, String> layoutResultMap = new HashMap<>();
  static {
    layoutResultMap.put("patternLayout", "#logback.classic pattern: %msg\ntest info message");
    layoutResultMap.put("xmlLayout", "<log4j:message>test info message</log4j:message>");
    layoutResultMap.put("htmlLayout", "<td class=\"Message\">test info message</td>");
  }

  private final PrintStream systemOut = System.out;

  private ByteArrayOutputStream outputStreamCaptor;

  private Path tempDirPath;

  @BeforeAll
  void beforeAll() throws IOException {
    tempDirPath = Files.createTempDirectory("logback-metadata-test");
  }

  @AfterAll
  void afterAll() {
    if (tempDirPath != null) {
      try {
        Files.walk(tempDirPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @BeforeEach
  public void setUp() {
    MDC.put("test", "GraalVM");
    context.putProperty("test", "GraalVM property");
    this.outputStreamCaptor = new ByteArrayOutputStream();
    System.setOut(new PrintStream(this.outputStreamCaptor));
  }

  @ParameterizedTest
  @ValueSource(strings = {"patternLayout", "xmlLayout"})
  void testLayouts(String layoutName) throws Exception {
    JoranConfigurator joranConfigurator = new JoranConfigurator();
    joranConfigurator.setContext(context);

    String configXml = LayoutTags.CONFIG_TAG.formatted(layoutTagMap.get(layoutName));
    joranConfigurator.doConfigure(new ByteArrayInputStream(configXml.getBytes()));

    Logger testLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    testLogger.info("test info message");

    String loggedMessage = outputStreamCaptor.toString();
    assertThat(loggedMessage).contains(layoutResultMap.get(layoutName));
  }

  @Test
  void testLoggingLevels() throws Exception {
    JoranConfigurator joranConfigurator = new JoranConfigurator();
    joranConfigurator.setContext(context);

    String configXml = """
            <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder>
                        <pattern>%p %msg%n</pattern>
                    </encoder>
                </appender>
                <root level="DEBUG">
                    <appender-ref ref="STDOUT"/>
                </root>
            </configuration>
            """;
    joranConfigurator.doConfigure(new ByteArrayInputStream(configXml.getBytes()));

    Logger testLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    testLogger.debug("debug message");
    testLogger.info("info message");
    testLogger.warn("warn message");
    testLogger.error("error message");

    String loggedMessage = outputStreamCaptor.toString();
    assertThat(loggedMessage).contains("DEBUG debug message");
    assertThat(loggedMessage).contains("INFO info message");
    assertThat(loggedMessage).contains("WARN warn message");
    assertThat(loggedMessage).contains("ERROR error message");
  }

  @Test
  void testFileAppender() throws Exception {
    JoranConfigurator joranConfigurator = new JoranConfigurator();
    joranConfigurator.setContext(context);

    String filePath = tempDirPath + "/log.txt";
    String filesTag = AppenderTags.FILE_TAG.formatted(filePath);
    String configXml = AppenderTags.CONFIG_TAG.formatted(filesTag);
    joranConfigurator.doConfigure(new ByteArrayInputStream(configXml.getBytes()));

    Logger testLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    testLogger.info("test info message");

    String loggedMessage = Files.readAllLines(Paths.get(filePath)).get(0);
    assertThat(loggedMessage).isEqualTo("test info message");
  }

  @Test
  void testRollingFileAppender() throws Exception {
    JoranConfigurator joranConfigurator = new JoranConfigurator();
    joranConfigurator.setContext(context);

    String filePath = tempDirPath + "/rolling-log.txt";
    String filesTag = AppenderTags.ROLLING_FILE_TAG.formatted(filePath);
    String configXml = AppenderTags.CONFIG_TAG.formatted(filesTag);
    joranConfigurator.doConfigure(new ByteArrayInputStream(configXml.getBytes()));

    Logger testLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    testLogger.info("test info message");

    String loggedMessage = Files.readAllLines(Paths.get(filePath)).get(0);
    assertThat(loggedMessage).isEqualTo("test info message");
  }

  @Test
  void consoleAppenderPropertySetter() {
    ConsoleAppender consoleAppender = new ConsoleAppender();
    PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), consoleAppender);
    propertySetter.setProperty("withJansi", "true");
    assertThat(consoleAppender.isWithJansi()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("converterSource")
  void shouldRegisterConverter(String converterName, String pattern) {
    PatternLayoutEncoder encoder = createEncoder("%" + pattern + " %n");
    ConsoleAppender<ILoggingEvent> consoleAppender = createConsoleAppender(encoder);
    Logger logger = getLogger(consoleAppender);
    logger.error(converterName, new IllegalArgumentException("test error"));
    assertThat(outputStreamCaptor.toString()).doesNotContain("PARSER_ERROR");
    cleanUp(encoder, consoleAppender, logger);
  }

  @Test
  void slf4jCanBeDetected() {
    boolean slf4jAvailable = isClassPresent("org.slf4j.Logger") && isClassPresent("org.slf4j.spi.SLF4JServiceProvider");
    assertThat(slf4jAvailable).isTrue();
  }

  private boolean isClassPresent(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }

  private static Stream<Arguments> converterSource() {
    return PatternLayout.DEFAULT_CONVERTER_MAP.entrySet().stream()
        .map(entry -> Arguments.of(entry.getValue(), entry.getKey()));
  }

  private PatternLayoutEncoder createEncoder(String pattern) {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(pattern);
    encoder.setCharset(StandardCharsets.UTF_8);
    encoder.start();
    return encoder;
  }

  private ConsoleAppender<ILoggingEvent> createConsoleAppender(PatternLayoutEncoder encoder) {
    ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    appender.setEncoder(encoder);
    appender.setContext(context);
    appender.start();
    return appender;
  }

  private Logger getLogger(ConsoleAppender<ILoggingEvent> appender) {
    Logger logger = context.getLogger(LogbackTests.class);
    logger.addAppender(appender);
    logger.setAdditive(false);
    return logger;
  }

  private void cleanUp(PatternLayoutEncoder encoder, ConsoleAppender<ILoggingEvent> appender, Logger logger) {
    encoder.stop();
    appender.stop();
    logger.detachAppender(appender);
  }

  @AfterEach
  public void tearDown() {
    System.setOut(systemOut);
  }

}
