/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.log4j.XMLLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
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

  private static final Map<String, String> layoutResultMap = new HashMap<>();
  static {
    layoutResultMap.put("patternLayout", "#logback.classic pattern: %msg\ntest info message");
    layoutResultMap.put("xmlLayout", "<log4j:message>test info message</log4j:message>");
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
  void testLayouts(String layoutName) {
    Layout<ILoggingEvent> layout = createLayout(layoutName);
    LayoutWrappingEncoder<ILoggingEvent> encoder = createLayoutWrappingEncoder(layout);
    ConsoleAppender<ILoggingEvent> appender = createConsoleAppender("layout-" + layoutName, encoder);
    Logger testLogger = getRootLogger(appender);
    try {
      testLogger.info("test info message");

      String loggedMessage = outputStreamCaptor.toString();
      assertThat(loggedMessage).contains(layoutResultMap.get(layoutName));
    } finally {
      cleanUp(encoder, appender, testLogger);
      layout.stop();
    }
  }

  @Test
  void testFileAppender() throws Exception {
    String filePath = tempDirPath.resolve("log.txt").toString();
    PatternLayoutEncoder encoder = createEncoder("%msg");
    FileAppender<ILoggingEvent> appender = new FileAppender<>();
    appender.setName("FILE");
    appender.setContext(context);
    appender.setFile(filePath);
    appender.setAppend(true);
    appender.setPrudent(false);
    appender.setEncoder(encoder);
    appender.setImmediateFlush(true);
    appender.start();

    Logger testLogger = getRootLogger(appender);
    try {
      testLogger.info("test info message");
      appender.stop();

      String loggedMessage = Files.readAllLines(Paths.get(filePath)).get(0);
      assertThat(loggedMessage).isEqualTo("test info message");
    } finally {
      cleanUp(encoder, appender, testLogger);
    }
  }

  @Test
  void testRollingFileAppender() throws Exception {
    String filePath = tempDirPath.resolve("rolling-log.txt").toString();
    PatternLayoutEncoder encoder = createEncoder("%msg");
    RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
    appender.setName("FILE");
    appender.setContext(context);
    appender.setFile(filePath);
    appender.setAppend(true);
    appender.setPrudent(false);
    appender.setEncoder(encoder);
    appender.setImmediateFlush(true);

    SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
    rollingPolicy.setContext(context);
    rollingPolicy.setParent(appender);
    rollingPolicy.setFileNamePattern(tempDirPath.resolve("rolling-log-%d{yyyy-MM-dd}.%i.txt").toString());
    rollingPolicy.setMaxFileSize(FileSize.valueOf("100MB"));
    rollingPolicy.setMaxHistory(60);
    rollingPolicy.setTotalSizeCap(FileSize.valueOf("20GB"));
    rollingPolicy.setCleanHistoryOnStart(false);
    rollingPolicy.start();

    SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
    triggeringPolicy.setContext(context);
    triggeringPolicy.setMaxFileSize(FileSize.valueOf("100MB"));
    triggeringPolicy.start();

    appender.setRollingPolicy(rollingPolicy);
    appender.setTriggeringPolicy(triggeringPolicy);
    appender.start();

    Logger testLogger = getRootLogger(appender);
    try {
      testLogger.info("test info message");
      appender.stop();

      String loggedMessage = Files.readAllLines(Paths.get(filePath)).get(0);
      assertThat(loggedMessage).isEqualTo("test info message");
    } finally {
      cleanUp(encoder, appender, testLogger);
      triggeringPolicy.stop();
      rollingPolicy.stop();
    }
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
    if (!PatternLayout.DEFAULT_CONVERTER_MAP.isEmpty()) {
      return PatternLayout.DEFAULT_CONVERTER_MAP.entrySet().stream()
          .map(entry -> Arguments.of(entry.getValue(), entry.getKey()));
    }
    return defaultConverterSupplierMap().entrySet().stream()
        .map(entry -> Arguments.of(entry.getValue().get().getClass().getName(), entry.getKey()));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Supplier<?>> defaultConverterSupplierMap() {
    try {
      Method method = PatternLayout.class.getMethod("getDefaultConverterSupplierMap");
      return (Map<String, Supplier<?>>) method.invoke(new PatternLayout());
    } catch (ReflectiveOperationException ignored) {
      // Fall back to the field for compatibility with older or transitional versions.
    }
    try {
      Field field = PatternLayout.class.getField("DEFAULT_CONVERTER_SUPPLIER_MAP");
      return (Map<String, Supplier<?>>) field.get(null);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Could not resolve the Logback default converter supplier map.", ex);
    }
  }

  private Layout<ILoggingEvent> createLayout(String layoutName) {
    if ("patternLayout".equals(layoutName)) {
      PatternLayout layout = new PatternLayout();
      layout.setContext(context);
      layout.setPattern("%msg");
      layout.setOutputPatternAsHeader(true);
      layout.start();
      return layout;
    }
    if ("xmlLayout".equals(layoutName)) {
      XMLLayout layout = new XMLLayout();
      layout.setContext(context);
      layout.setLocationInfo(true);
      layout.setProperties(true);
      layout.start();
      return layout;
    }
    throw new IllegalArgumentException("Unsupported layout name: " + layoutName);
  }

  private LayoutWrappingEncoder<ILoggingEvent> createLayoutWrappingEncoder(Layout<ILoggingEvent> layout) {
    LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
    encoder.setContext(context);
    encoder.setLayout(layout);
    encoder.setCharset(StandardCharsets.UTF_8);
    encoder.start();
    return encoder;
  }

  private PatternLayoutEncoder createEncoder(String pattern) {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(pattern);
    encoder.setCharset(StandardCharsets.UTF_8);
    encoder.start();
    return encoder;
  }

  private ConsoleAppender<ILoggingEvent> createConsoleAppender(Encoder<ILoggingEvent> encoder) {
    return createConsoleAppender("console", encoder);
  }

  private ConsoleAppender<ILoggingEvent> createConsoleAppender(String name, Encoder<ILoggingEvent> encoder) {
    ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    appender.setName(name);
    appender.setEncoder(encoder);
    appender.setContext(context);
    appender.start();
    return appender;
  }

  private Logger getRootLogger(Appender<ILoggingEvent> appender) {
    Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    logger.addAppender(appender);
    return logger;
  }

  private Logger getLogger(ConsoleAppender<ILoggingEvent> appender) {
    Logger logger = context.getLogger(LogbackTests.class);
    logger.addAppender(appender);
    logger.setAdditive(false);
    return logger;
  }

  private void cleanUp(Encoder<ILoggingEvent> encoder, Appender<ILoggingEvent> appender, Logger logger) {
    encoder.stop();
    appender.stop();
    logger.detachAppender(appender);
  }

  @AfterEach
  public void tearDown() {
    System.setOut(systemOut);
  }

}
