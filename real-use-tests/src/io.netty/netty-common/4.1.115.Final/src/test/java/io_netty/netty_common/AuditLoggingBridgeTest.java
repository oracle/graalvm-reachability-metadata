/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the internal logging facade of netty-common: `InternalLoggerFactory` with its
 * pluggable SLF4J and `java.util.logging` backends, level checks, `{}` message formatting and
 * throwable hand-over.
 *
 * A payment gateway routes the logging of its Netty-based components into Logback by installing
 * the SLF4J factory as Netty's default. Its audit component logs through an `InternalLogger`: the
 * Logback level decides which messages are enabled, placeholders are filled in, a failure carries
 * its exception and a direct call's location points at the audit code rather than at the bridge. The
 * gateway can later switch the bridge to `java.util.logging` for a component that ships its logs
 * through a JUL handler.
 */
class AuditLoggingBridgeTest {

    private InternalLoggerFactory previousFactory;

    private Logger logbackLogger;

    private LocatingAppender captured;

    private java.util.logging.Logger julLogger;

    private RecordingHandler julRecords;

    @BeforeEach
    void routeNettyLoggingToLogback() {
        previousFactory = InternalLoggerFactory.getDefaultFactory();
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

        logbackLogger = (Logger) LoggerFactory.getLogger(PaymentAudit.class);
        logbackLogger.setLevel(Level.INFO);
        logbackLogger.setAdditive(false);
        captured = new LocatingAppender();
        captured.setContext(logbackLogger.getLoggerContext());
        captured.start();
        logbackLogger.addAppender(captured);
    }

    @AfterEach
    void restoreLogging() {
        logbackLogger.detachAppender(captured);
        captured.stop();
        logbackLogger.setLevel(null);
        logbackLogger.setAdditive(true);
        if (julLogger != null) {
            julLogger.removeHandler(julRecords);
        }
        InternalLoggerFactory.setDefaultFactory(previousFactory);
    }

    @Test
    void auditMessagesFollowTheLogbackLevelAndKeepTheirArgumentsAndFailures() {
        PaymentAudit audit = new PaymentAudit();

        assertThat(audit.logger.name()).isEqualTo(PaymentAudit.class.getName());
        assertThat(audit.logger.isTraceEnabled()).isFalse();
        assertThat(audit.logger.isDebugEnabled()).isFalse();
        assertThat(audit.logger.isInfoEnabled()).isTrue();
        assertThat(audit.logger.isEnabled(InternalLogLevel.WARN)).isTrue();

        audit.authorized("PAY-1", "alice", 1250);
        audit.inspected("PAY-1");
        audit.declined("PAY-2", "bob", new IllegalStateException("card expired"));
        audit.flagged("PAY-3", "carol", "velocity", 7);

        assertThat(captured.list).extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.INFO, Level.WARN, Level.ERROR);
        assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "payment PAY-1 authorized for alice: 1250 cents",
                "payment PAY-2 declined for bob",
                "payment PAY-3 of carol flagged by velocity rule after 7 attempts");
        assertThat(captured.list).extracting(ILoggingEvent::getLoggerName)
                .containsOnly(PaymentAudit.class.getName());

        IThrowableProxy failure = captured.list.get(1).getThrowableProxy();
        assertThat(failure).isNotNull();
        assertThat(failure.getClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failure.getMessage()).isEqualTo("card expired");
        assertThat(captured.list.get(0).getThrowableProxy()).isNull();

        for (ILoggingEvent event : captured.list.subList(0, 2)) {
            assertThat(event.getCallerData()).isNotEmpty();
            assertThat(event.getCallerData()[0].getClassName()).isEqualTo(PaymentAudit.class.getName());
        }
    }

    @Test
    void raisingTheLogbackLevelAtRuntimeEnablesDebugAuditing() {
        PaymentAudit audit = new PaymentAudit();

        audit.inspected("PAY-4");
        assertThat(captured.list).isEmpty();

        logbackLogger.setLevel(Level.DEBUG);
        assertThat(audit.logger.isDebugEnabled()).isTrue();
        audit.inspected("PAY-4");
        audit.logger.log(InternalLogLevel.TRACE, "trace for {}", "PAY-4");

        assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("inspecting payment PAY-4");
        assertThat(captured.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void switchingTheBridgeToJdkLoggingRoutesNewLoggersThroughJul() {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
        julLogger = java.util.logging.Logger.getLogger(SettlementJob.class.getName());
        julLogger.setLevel(java.util.logging.Level.FINE);
        julLogger.setUseParentHandlers(false);
        julRecords = new RecordingHandler();
        julLogger.addHandler(julRecords);

        SettlementJob job = new SettlementJob();
        job.settle("batch-9", 42);
        job.failed("batch-10", new IllegalArgumentException("unknown currency"));

        assertThat(job.logger.name()).isEqualTo(SettlementJob.class.getName());
        assertThat(job.logger.isDebugEnabled()).isTrue();
        assertThat(job.logger.isTraceEnabled()).isFalse();
        assertThat(julRecords.records).extracting(LogRecord::getLevel).containsExactly(
                java.util.logging.Level.FINE, java.util.logging.Level.SEVERE);
        assertThat(julRecords.records).extracting(LogRecord::getMessage).containsExactly(
                "settled batch-9 with 42 payments", "settlement of batch-10 failed");
        assertThat(julRecords.records.get(1).getThrown())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown currency");
        assertThat(julRecords.records).extracting(LogRecord::getSourceClassName)
                .containsOnly(SettlementJob.class.getName());
        assertThat(captured.list).isEmpty();
    }

    /** The gateway's audit component, logging through Netty's facade like Netty's own classes. */
    static final class PaymentAudit {

        final InternalLogger logger = InternalLoggerFactory.getInstance(PaymentAudit.class);

        void authorized(String paymentId, String customer, long cents) {
            logger.info("payment {} authorized for {}: {} cents", paymentId, customer, cents);
        }

        void inspected(String paymentId) {
            logger.debug("inspecting payment {}", paymentId);
        }

        void declined(String paymentId, String customer, Throwable cause) {
            logger.warn("payment " + paymentId + " declined for " + customer, cause);
        }

        void flagged(String paymentId, String customer, String rule, int attempts) {
            List<Object> arguments = new ArrayList<>();
            arguments.add(paymentId);
            arguments.add(customer);
            arguments.add(rule);
            arguments.add(attempts);
            logger.log(InternalLogLevel.ERROR, "payment {} of {} flagged by {} rule after {} attempts",
                    arguments.toArray());
        }
    }

    /** A settlement job whose logs the gateway ships through a `java.util.logging` handler. */
    static final class SettlementJob {

        final InternalLogger logger = InternalLoggerFactory.getInstance(SettlementJob.class);

        void settle(String batch, int payments) {
            logger.debug("settled {} with {} payments", batch, payments);
        }

        void failed(String batch, Throwable cause) {
            logger.error("settlement of " + batch + " failed", cause);
        }
    }

    /** Keeps Logback events with their caller location, which Logback resolves only on request. */
    static final class LocatingAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            event.getCallerData();
            super.append(event);
        }
    }

    /** Collects the records JUL publishes so the gateway can ship them. */
    static final class RecordingHandler extends Handler {

        final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
