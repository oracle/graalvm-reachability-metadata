/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.MarkerFilter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.BasicSequenceNumberGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class Logback_classicTest {

    private LoggerContext context;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        context = new LoggerContext();
        context.setName("logback-classic-test-" + System.nanoTime());
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.setSequenceNumberGenerator(new BasicSequenceNumberGenerator());
        context.start();
        tempDir = Files.createTempDirectory("logback-classic-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        MDC.clear();
        if (context != null) {
            context.stop();
        }
        if (tempDir != null) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Test
    void patternLayoutEncoderFormatsLevelLoggerMarkerMdcAndThrowable() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PatternLayoutEncoder encoder = patternEncoder("%level|%logger{0}|%marker|%X{requestId}|%msg|%ex{short}%n");
        OutputStreamAppender<ILoggingEvent> appender = outputStreamAppender("pattern", encoder, outputStream);
        Logger logger = logger("pattern.formatting", appender);
        Marker marker = MarkerFactory.getMarker("SECURITY");

        MDC.put("requestId", "request-42");
        logger.error(marker, "login failed", new IllegalStateException("denied"));

        String logLine = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(logLine)
                .contains("ERROR|formatting|SECURITY|request-42|login failed|java.lang.IllegalStateException: denied")
                .contains("at ")
                .doesNotContain("PARSER_ERROR");
    }

    @Test
    void jsonEncoderWritesStructuredEventsWithMdcMarkersArgumentsAndKeyValuePairs() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonEncoder encoder = jsonEncoder();
        OutputStreamAppender<ILoggingEvent> appender = outputStreamAppender("json", encoder, outputStream);
        Logger logger = logger("json.structured", appender);
        Marker marker = MarkerFactory.getMarker("AUDIT");

        MDC.put("requestId", "request-99");
        logger.atInfo()
                .addMarker(marker)
                .addKeyValue("operation", "checkout")
                .addArgument("cart-7")
                .log("processed {}");

        String json = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"level\":\"INFO\"")
                .contains("\"loggerName\":\"json.structured\"")
                .contains("\"markers\": [\"AUDIT\"]")
                .contains("\"mdc\": {\"requestId\":\"request-99\"}")
                .contains("\"kvpList\": [{\"operation\":\"checkout\"}]")
                .contains("\"message\":\"processed {}\"")
                .contains("\"formattedMessage\":\"processed cart-7\"")
                .contains("\"arguments\": [\"cart-7\"]")
                .contains("\"throwable\":null");
    }

    @Test
    void thresholdFilterDeniesEventsBelowConfiguredLevel() {
        ThresholdFilter filter = new ThresholdFilter();
        filter.setContext(context);
        filter.setLevel("INFO");
        filter.start();
        ListAppender<ILoggingEvent> appender = listAppender("threshold");
        appender.addFilter(filter);
        Logger logger = logger("threshold.filter", appender);
        logger.setLevel(Level.TRACE);

        logger.debug("debug is below threshold");
        logger.info("info is accepted");
        logger.warn("warn is accepted");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("info is accepted", "warn is accepted");
    }

    @Test
    void loggerHierarchyUsesInheritedLevelsAndAdditivity() {
        ListAppender<ILoggingEvent> parentAppender = listAppender("parent");
        ListAppender<ILoggingEvent> childAppender = listAppender("child");
        Logger parent = logger("application.service", parentAppender);
        Logger child = context.getLogger("application.service.worker");
        child.addAppender(childAppender);
        parent.setLevel(Level.WARN);

        child.info("inherited level rejects this message");
        child.warn("inherited level accepts this message");

        assertThat(childAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("inherited level accepts this message");
        assertThat(parentAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("inherited level accepts this message");

        child.setAdditive(false);
        child.error("only the child appender sees this message");

        assertThat(childAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("inherited level accepts this message", "only the child appender sees this message");
        assertThat(parentAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("inherited level accepts this message");
    }

    @Test
    void asyncAppenderForwardsQueuedEventsBeforeStop() {
        ListAppender<ILoggingEvent> delegate = listAppender("delegate");
        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("async");
        asyncAppender.setQueueSize(32);
        asyncAppender.setDiscardingThreshold(0);
        asyncAppender.addAppender(delegate);
        asyncAppender.start();
        Logger logger = logger("async.forwarding", asyncAppender);

        for (int index = 0; index < 12; index++) {
            logger.info("queued-{}", index);
        }
        asyncAppender.stop();

        assertThat(delegate.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "queued-0",
                        "queued-1",
                        "queued-2",
                        "queued-3",
                        "queued-4",
                        "queued-5",
                        "queued-6",
                        "queued-7",
                        "queued-8",
                        "queued-9",
                        "queued-10",
                        "queued-11");
    }

    @Test
    void turboMarkerFilterDeniesMarkedEventsBeforeAppenderProcessing() {
        MarkerFilter filter = new MarkerFilter();
        filter.setContext(context);
        filter.setMarker("SUPPRESS");
        filter.setOnMatch("DENY");
        filter.setOnMismatch("NEUTRAL");
        filter.start();
        context.addTurboFilter(filter);
        ListAppender<ILoggingEvent> appender = listAppender("turbo");
        Logger logger = logger("turbo.marker", appender);
        Marker suppressMarker = MarkerFactory.getMarker("SUPPRESS");

        logger.info("visible event");
        logger.warn(suppressMarker, "suppressed event");
        logger.error("another visible event");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("visible event", "another visible event");
    }

    @Test
    void joranConfiguratorCreatesFileAppenderWithContextProperty() throws Exception {
        Path logFile = tempDir.resolve("joran-file.log");
        context.putProperty("LOG_FILE", logFile.toString());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        byte[] configuration = fileAppenderConfiguration().getBytes(StandardCharsets.UTF_8);
        configurator.doConfigure(new ByteArrayInputStream(configuration));
        Logger logger = context.getLogger("configured.file");
        logger.info("from XML configuration");
        context.stop();

        assertThat(Files.readString(logFile, StandardCharsets.UTF_8))
                .contains("INFO|configured.file|from XML configuration");
    }

    @Test
    void siftingAppenderRoutesEventsByMdcValue() throws Exception {
        context.putProperty("LOG_DIR", tempDir.toString());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        byte[] configuration = siftingAppenderConfiguration().getBytes(StandardCharsets.UTF_8);
        configurator.doConfigure(new ByteArrayInputStream(configuration));
        Logger logger = context.getLogger("configured.sifting");
        logger.setLevel(Level.INFO);
        MDC.put("tenant", "alpha");
        logger.info("alpha event");
        MDC.put("tenant", "beta");
        logger.info("beta event");
        MDC.remove("tenant");
        logger.info("default event");
        context.stop();

        assertThat(Files.readString(tempDir.resolve("alpha.log"), StandardCharsets.UTF_8))
                .contains("alpha|alpha event");
        assertThat(Files.readString(tempDir.resolve("beta.log"), StandardCharsets.UTF_8))
                .contains("beta|beta event");
        assertThat(Files.readString(tempDir.resolve("unknown.log"), StandardCharsets.UTF_8)).contains("default event");
    }

    @Test
    void sequenceNumberAndCallerDataAreAvailableOnLoggingEvents() {
        ListAppender<ILoggingEvent> appender = listAppender("events");
        Logger logger = logger("event.details", appender);

        logger.info("first");
        logger.info("second");

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getSequenceNumber()).isGreaterThan(events.get(0).getSequenceNumber());
        assertThat(events.get(0).getCallerData())
                .extracting(StackTraceElement::getMethodName)
                .contains("sequenceNumberAndCallerDataAreAvailableOnLoggingEvents");
    }

    private Logger logger(String name, Appender<ILoggingEvent> appender) {
        Logger logger = context.getLogger(name);
        logger.setAdditive(false);
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
        return logger;
    }

    private PatternLayoutEncoder patternEncoder(String pattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.setPattern(pattern);
        encoder.start();
        return encoder;
    }

    private JsonEncoder jsonEncoder() {
        JsonEncoder encoder = new JsonEncoder();
        encoder.setContext(context);
        encoder.setWithTimestamp(false);
        encoder.setWithNanoseconds(false);
        encoder.setWithSequenceNumber(false);
        encoder.setWithThreadName(false);
        encoder.setWithContext(false);
        encoder.setWithFormattedMessage(true);
        encoder.start();
        return encoder;
    }

    private OutputStreamAppender<ILoggingEvent> outputStreamAppender(
            String name, Encoder<ILoggingEvent> encoder, ByteArrayOutputStream outputStream) {
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setName(name);
        appender.setEncoder(encoder);
        appender.setOutputStream(outputStream);
        appender.start();
        return appender;
    }

    private ListAppender<ILoggingEvent> listAppender(String name) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.setName(name);
        appender.start();
        return appender;
    }

    private static String fileAppenderConfiguration() {
        return """
                <configuration>
                    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
                        <file>${LOG_FILE}</file>
                        <append>false</append>
                        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                            <pattern>%level|%logger|%msg%n</pattern>
                        </encoder>
                    </appender>
                    <logger name="configured.file" level="INFO" additivity="false">
                        <appender-ref ref="FILE"/>
                    </logger>
                </configuration>
                """;
    }

    private static String siftingAppenderConfiguration() {
        return """
                <configuration>
                    <appender name="SIFT" class="ch.qos.logback.classic.sift.SiftingAppender">
                        <discriminator class="ch.qos.logback.classic.sift.MDCBasedDiscriminator">
                            <key>tenant</key>
                            <defaultValue>unknown</defaultValue>
                        </discriminator>
                        <sift>
                            <appender name="FILE-${tenant}" class="ch.qos.logback.core.FileAppender">
                                <file>${LOG_DIR}/${tenant}.log</file>
                                <append>false</append>
                                <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                                    <pattern>%X{tenant:-unknown}|%msg%n</pattern>
                                </encoder>
                            </appender>
                        </sift>
                    </appender>
                    <logger name="configured.sifting" level="INFO" additivity="false">
                        <appender-ref ref="SIFT"/>
                    </logger>
                </configuration>
                """;
    }
}
