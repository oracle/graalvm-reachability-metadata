/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.logback;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.slf4j.impl.StaticLoggerBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class LogbackTests {

  private static final LoggerContext context = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();

  private final PrintStream systemOut = System.out;

  private ByteArrayOutputStream outputStreamCaptor;

  @BeforeEach
  public void setUp() {
    MDC.put("test", "GraalVM");
    context.putProperty("test", "GraalVM property");
    this.outputStreamCaptor = new ByteArrayOutputStream();
    System.setOut(new PrintStream(this.outputStreamCaptor));
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

