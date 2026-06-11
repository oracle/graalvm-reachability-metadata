/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;

public class Org_osgi_service_logTest {
    @Test
    void logLevelsExposeOrderedSeverityValues() {
        assertThat(List.of(LogLevel.values())).containsExactly(
                LogLevel.AUDIT,
                LogLevel.ERROR,
                LogLevel.WARN,
                LogLevel.INFO,
                LogLevel.DEBUG,
                LogLevel.TRACE);
        assertThat(LogLevel.DEBUG.implies(LogLevel.INFO)).isTrue();
        assertThat(LogLevel.INFO.implies(LogLevel.DEBUG)).isFalse();
    }

    @Test
    void loggingOverloadsCreateEntriesWithExpectedOptionalFields() {
        InMemoryLogService logService = new InMemoryLogService();
        TestServiceReference reference = new TestServiceReference("service.id", 7L);
        IllegalStateException exception = new IllegalStateException("not available");
        long before = System.currentTimeMillis();

        logService.log(LogLevel.INFO.ordinal(), "started");
        logService.log(LogLevel.WARN.ordinal(), "warning", exception);
        logService.log(reference, LogLevel.DEBUG.ordinal(), "service debug");
        logService.log(reference, LogLevel.ERROR.ordinal(), "service failed", exception);
        logService.log(99, null, null);
        long after = System.currentTimeMillis();

        List<LogEntry> entries = logService.entriesInMostRecentFirstOrder();
        assertThat(entries).hasSize(5);

        LogEntry nullPayload = entries.get(0);
        assertThat(nullPayload.getLogLevel()).isEqualTo(LogLevel.TRACE);
        assertThat(nullPayload.getMessage()).isNull();
        assertThat(nullPayload.getException()).isNull();
        assertThat(nullPayload.getServiceReference()).isNull();

        LogEntry serviceFailure = entries.get(1);
        assertThat(serviceFailure.getLogLevel()).isEqualTo(LogLevel.ERROR);
        assertThat(serviceFailure.getMessage()).isEqualTo("service failed");
        assertThat(serviceFailure.getException()).isSameAs(exception);
        assertThat(serviceFailure.getServiceReference()).isSameAs(reference);
        assertThat(serviceFailure.getBundle()).isNull();
        assertThat(serviceFailure.getTime()).isBetween(before, after);

        LogEntry serviceDebug = entries.get(2);
        assertThat(serviceDebug.getLogLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(serviceDebug.getMessage()).isEqualTo("service debug");
        assertThat(serviceDebug.getException()).isNull();
        assertThat(serviceDebug.getServiceReference()).isSameAs(reference);

        LogEntry warning = entries.get(3);
        assertThat(warning.getLogLevel()).isEqualTo(LogLevel.WARN);
        assertThat(warning.getMessage()).isEqualTo("warning");
        assertThat(warning.getException()).isSameAs(exception);
        assertThat(warning.getServiceReference()).isNull();

        LogEntry started = entries.get(4);
        assertThat(started.getLogLevel()).isEqualTo(LogLevel.INFO);
        assertThat(started.getMessage()).isEqualTo("started");
        assertThat(started.getException()).isNull();
        assertThat(started.getServiceReference()).isNull();
    }

    @Test
    void logListenerIsAnEventListenerAndReceivesEntriesUntilRemoved() {
        InMemoryLogService logService = new InMemoryLogService();
        List<LogEntry> firstListenerEntries = new ArrayList<>();
        List<LogEntry> secondListenerEntries = new ArrayList<>();
        LogListener firstListener = firstListenerEntries::add;
        LogListener secondListener = secondListenerEntries::add;
        LogListener absentListener = entry -> {
            throw new AssertionError("unregistered listener should not be called");
        };

        assertThat(firstListener).isInstanceOf(EventListener.class);

        logService.addLogListener(firstListener);
        logService.addLogListener(firstListener);
        logService.addLogListener(secondListener);
        logService.removeLogListener(absentListener);

        logService.log(LogLevel.INFO.ordinal(), "first");
        logService.log(LogLevel.WARN.ordinal(), "second");
        logService.removeLogListener(firstListener);
        logService.log(LogLevel.ERROR.ordinal(), "third");

        assertThat(firstListenerEntries).extracting(LogEntry::getMessage).containsExactly("first", "second");
        assertThat(secondListenerEntries).extracting(LogEntry::getMessage).containsExactly("first", "second", "third");
    }

    @Test
    void logReaderReturnsMostRecentEntriesInAnEnumerationSnapshot() {
        InMemoryLogService logService = new InMemoryLogService();
        LogReaderService readerService = logService;

        logService.log(LogLevel.INFO.ordinal(), "oldest");
        logService.log(LogLevel.WARN.ordinal(), "middle");
        logService.log(LogLevel.ERROR.ordinal(), "newest");

        Enumeration<LogEntry> logSnapshot = readerService.getLog();
        logService.log(LogLevel.DEBUG.ordinal(), "after snapshot");

        List<String> messages = new ArrayList<>();
        while (logSnapshot.hasMoreElements()) {
            messages.add(logSnapshot.nextElement().getMessage());
        }

        assertThat(messages).containsExactly("newest", "middle", "oldest");
        assertThat(logService.entriesInMostRecentFirstOrder())
                .extracting(LogEntry::getMessage)
                .containsExactly("after snapshot", "newest", "middle", "oldest");
    }

    @Test
    void logReaderRegistersEqualButDistinctListenersIndependently() {
        InMemoryLogService logService = new InMemoryLogService();
        List<String> firstListenerMessages = new ArrayList<>();
        List<String> secondListenerMessages = new ArrayList<>();
        LogListener firstListener = new EqualLogListener(firstListenerMessages);
        LogListener secondListener = new EqualLogListener(secondListenerMessages);

        assertThat(firstListener).isEqualTo(secondListener).isNotSameAs(secondListener);

        logService.addLogListener(firstListener);
        logService.addLogListener(secondListener);
        logService.log(LogLevel.INFO.ordinal(), "delivered to both");

        logService.removeLogListener(firstListener);
        logService.log(LogLevel.WARN.ordinal(), "delivered to second");

        assertThat(firstListenerMessages).containsExactly("delivered to both");
        assertThat(secondListenerMessages).containsExactly("delivered to both", "delivered to second");
    }

    @Test
    void logEntryExposesTheBundleThatCreatedTheEntry() {
        TestBundle bundle = new TestBundle(23L, "example.logging.bundle");
        InMemoryLogService logService = new InMemoryLogService(bundle);
        LogReaderService readerService = logService;
        List<LogEntry> listenerEntries = new ArrayList<>();

        logService.addLogListener(listenerEntries::add);
        logService.log(LogLevel.INFO.ordinal(), "bundle scoped message");

        LogEntry readerEntry = readerService.getLog().nextElement();
        assertThat(readerEntry.getBundle()).isSameAs(bundle);
        assertThat(readerEntry.getBundle().getBundleId()).isEqualTo(23L);
        assertThat(readerEntry.getBundle().getSymbolicName()).isEqualTo("example.logging.bundle");
        assertThat(listenerEntries).singleElement().satisfies(entry -> assertThat(entry.getBundle()).isSameAs(bundle));
    }

    @Test
    void loggerFactoryCreatesTypedLoggersAndEntriesExposeNewAttributes() {
        TestBundle bundle = new TestBundle(42L, "example.logger.bundle");
        InMemoryLogService logService = new InMemoryLogService(bundle);
        String currentThreadName = Thread.currentThread().getName();

        Logger namedLogger = logService.getLogger("example.logger");
        FormatterLogger formatterLogger = logService.getLogger(bundle, "example.formatter", FormatterLogger.class);
        Logger classLogger = logService.getLogger(Org_osgi_service_logTest.class, Logger.class);

        assertThat(namedLogger.getName()).isEqualTo("example.logger");
        assertThat(formatterLogger.getName()).isEqualTo("example.formatter");
        assertThat(classLogger.getName()).isEqualTo(Org_osgi_service_logTest.class.getName());
        assertThat(namedLogger.isInfoEnabled()).isTrue();
        assertThat(formatterLogger.isWarnEnabled()).isTrue();

        namedLogger.info("logger api message");
        formatterLogger.warn("formatted %s", "message");

        List<LogEntry> entries = logService.entriesInMostRecentFirstOrder();
        assertThat(entries).hasSize(2);

        LogEntry formattedEntry = entries.get(0);
        assertThat(formattedEntry.getBundle()).isSameAs(bundle);
        assertThat(formattedEntry.getLoggerName()).isEqualTo("example.formatter");
        assertThat(formattedEntry.getLogLevel()).isEqualTo(LogLevel.WARN);
        assertThat(formattedEntry.getMessage()).isEqualTo("formatted message");
        assertThat(formattedEntry.getSequence()).isGreaterThan(entries.get(1).getSequence());
        assertThat(formattedEntry.getThreadInfo()).contains(currentThreadName);
        assertThat(formattedEntry.getLocation()).isNotNull();

        LogEntry namedEntry = entries.get(1);
        assertThat(namedEntry.getLoggerName()).isEqualTo("example.logger");
        assertThat(namedEntry.getLogLevel()).isEqualTo(LogLevel.INFO);
        assertThat(namedEntry.getMessage()).isEqualTo("logger api message");
    }

    private static final class InMemoryLogService implements LogService, LogReaderService {
        private final Bundle bundle;
        private final List<LogEntry> entries = new ArrayList<>();
        private final List<LogListener> listeners = new ArrayList<>();
        private final AtomicLong nextSequence = new AtomicLong();

        private InMemoryLogService() {
            this(null);
        }

        private InMemoryLogService(Bundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public Logger getLogger(String name) {
            return getLogger(name, Logger.class);
        }

        @Override
        public Logger getLogger(Class<?> clazz) {
            return getLogger(clazz, Logger.class);
        }

        @Override
        public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
            return getLogger(bundle, name, loggerType);
        }

        @Override
        public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
            return getLogger(clazz.getName(), loggerType);
        }

        @Override
        public <L extends Logger> L getLogger(Bundle loggerBundle, String name, Class<L> loggerType) {
            SimpleLogger logger = new SimpleLogger(this, loggerBundle, name,
                    FormatterLogger.class.equals(loggerType));
            if (!loggerType.isInstance(logger)) {
                throw new IllegalArgumentException("Unsupported logger type: " + loggerType.getName());
            }
            return loggerType.cast(logger);
        }

        @Override
        public void log(int level, String message) {
            log(null, level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            log(null, level, message, exception);
        }

        @Override
        public void log(ServiceReference<?> sr, int level, String message) {
            log(sr, level, message, null);
        }

        @Override
        public void log(ServiceReference<?> sr, int level, String message, Throwable exception) {
            append(bundle, sr, level, toLogLevel(level), "LogService", message, exception);
        }

        @Override
        public void addLogListener(LogListener listener) {
            if (!containsSameListener(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public void removeLogListener(LogListener listener) {
            listeners.removeIf(candidate -> candidate == listener);
        }

        @Override
        public Enumeration<LogEntry> getLog() {
            return Collections.enumeration(new ArrayList<>(entries));
        }

        private void append(Bundle entryBundle, ServiceReference<?> serviceReference, int level, LogLevel logLevel,
                String loggerName, String message, Throwable exception) {
            LogEntry entry = new SimpleLogEntry(entryBundle, serviceReference, level, logLevel, loggerName, message,
                    exception, System.currentTimeMillis(), nextSequence.getAndIncrement(),
                    Thread.currentThread().getName(), new Throwable().getStackTrace()[2]);
            entries.add(0, entry);
            for (LogListener listener : new ArrayList<>(listeners)) {
                listener.logged(entry);
            }
        }

        private static LogLevel toLogLevel(int level) {
            switch (level) {
                case 1:
                    return LogLevel.ERROR;
                case 2:
                    return LogLevel.WARN;
                case 3:
                    return LogLevel.INFO;
                case 4:
                    return LogLevel.DEBUG;
                default:
                    return LogLevel.TRACE;
            }
        }

        private boolean containsSameListener(LogListener listener) {
            for (LogListener candidate : listeners) {
                if (candidate == listener) {
                    return true;
                }
            }
            return false;
        }

        private List<LogEntry> entriesInMostRecentFirstOrder() {
            return new ArrayList<>(entries);
        }
    }

    private static final class SimpleLogger implements FormatterLogger {
        private final InMemoryLogService logService;
        private final Bundle bundle;
        private final String name;
        private final boolean formatterStyle;

        private SimpleLogger(InMemoryLogService logService, Bundle bundle, String name, boolean formatterStyle) {
            this.logService = logService;
            this.bundle = bundle;
            this.name = name;
            this.formatterStyle = formatterStyle;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public void trace(String message) {
            log(LogLevel.TRACE, message);
        }

        @Override
        public void trace(String format, Object arg) {
            log(LogLevel.TRACE, formatMessage(format, arg));
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            log(LogLevel.TRACE, formatMessage(format, arg1, arg2));
        }

        @Override
        public void trace(String format, Object... arguments) {
            log(LogLevel.TRACE, formatMessage(format, arguments));
        }

        @Override
        public <E extends Exception> void trace(LoggerConsumer<E> consumer) throws E {
            consumer.accept(this);
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String message) {
            log(LogLevel.DEBUG, message);
        }

        @Override
        public void debug(String format, Object arg) {
            log(LogLevel.DEBUG, formatMessage(format, arg));
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            log(LogLevel.DEBUG, formatMessage(format, arg1, arg2));
        }

        @Override
        public void debug(String format, Object... arguments) {
            log(LogLevel.DEBUG, formatMessage(format, arguments));
        }

        @Override
        public <E extends Exception> void debug(LoggerConsumer<E> consumer) throws E {
            consumer.accept(this);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
            log(LogLevel.INFO, message);
        }

        @Override
        public void info(String format, Object arg) {
            log(LogLevel.INFO, formatMessage(format, arg));
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            log(LogLevel.INFO, formatMessage(format, arg1, arg2));
        }

        @Override
        public void info(String format, Object... arguments) {
            log(LogLevel.INFO, formatMessage(format, arguments));
        }

        @Override
        public <E extends Exception> void info(LoggerConsumer<E> consumer) throws E {
            consumer.accept(this);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String message) {
            log(LogLevel.WARN, message);
        }

        @Override
        public void warn(String format, Object arg) {
            log(LogLevel.WARN, formatMessage(format, arg));
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            log(LogLevel.WARN, formatMessage(format, arg1, arg2));
        }

        @Override
        public void warn(String format, Object... arguments) {
            log(LogLevel.WARN, formatMessage(format, arguments));
        }

        @Override
        public <E extends Exception> void warn(LoggerConsumer<E> consumer) throws E {
            consumer.accept(this);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
            log(LogLevel.ERROR, message);
        }

        @Override
        public void error(String format, Object arg) {
            log(LogLevel.ERROR, formatMessage(format, arg));
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            log(LogLevel.ERROR, formatMessage(format, arg1, arg2));
        }

        @Override
        public void error(String format, Object... arguments) {
            log(LogLevel.ERROR, formatMessage(format, arguments));
        }

        @Override
        public <E extends Exception> void error(LoggerConsumer<E> consumer) throws E {
            consumer.accept(this);
        }

        @Override
        public void audit(String message) {
            log(LogLevel.AUDIT, message);
        }

        @Override
        public void audit(String format, Object arg) {
            log(LogLevel.AUDIT, formatMessage(format, arg));
        }

        @Override
        public void audit(String format, Object arg1, Object arg2) {
            log(LogLevel.AUDIT, formatMessage(format, arg1, arg2));
        }

        @Override
        public void audit(String format, Object... arguments) {
            log(LogLevel.AUDIT, formatMessage(format, arguments));
        }

        private void log(LogLevel logLevel, String message) {
            logService.append(bundle, null, logLevel.ordinal(), logLevel, name, message, null);
        }

        private String formatMessage(String format, Object... arguments) {
            if (format == null || arguments.length == 0) {
                return format;
            }
            if (formatterStyle) {
                return String.format(Locale.ROOT, format, arguments);
            }
            String formatted = format;
            for (Object argument : arguments) {
                int placeholder = formatted.indexOf("{}");
                if (placeholder < 0) {
                    break;
                }
                formatted = formatted.substring(0, placeholder) + argument + formatted.substring(placeholder + 2);
            }
            return formatted;
        }
    }

    private static final class EqualLogListener implements LogListener {
        private final List<String> messages;

        private EqualLogListener(List<String> messages) {
            this.messages = messages;
        }

        @Override
        public void logged(LogEntry entry) {
            messages.add(entry.getMessage());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualLogListener;
        }

        @Override
        public int hashCode() {
            return EqualLogListener.class.hashCode();
        }
    }

    private static final class SimpleLogEntry implements LogEntry {
        private final Bundle bundle;
        private final ServiceReference<?> serviceReference;
        private final int level;
        private final LogLevel logLevel;
        private final String loggerName;
        private final String message;
        private final Throwable exception;
        private final long time;
        private final long sequence;
        private final String threadInfo;
        private final StackTraceElement location;

        private SimpleLogEntry(Bundle bundle, ServiceReference<?> serviceReference, int level, LogLevel logLevel,
                String loggerName, String message, Throwable exception, long time, long sequence, String threadInfo,
                StackTraceElement location) {
            this.bundle = bundle;
            this.serviceReference = serviceReference;
            this.level = level;
            this.logLevel = logLevel;
            this.loggerName = loggerName;
            this.message = message;
            this.exception = exception;
            this.time = time;
            this.sequence = sequence;
            this.threadInfo = threadInfo;
            this.location = location;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public ServiceReference<?> getServiceReference() {
            return serviceReference;
        }

        @Override
        public int getLevel() {
            return level;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public Throwable getException() {
            return exception;
        }

        @Override
        public long getTime() {
            return time;
        }

        @Override
        public LogLevel getLogLevel() {
            return logLevel;
        }

        @Override
        public String getLoggerName() {
            return loggerName;
        }

        @Override
        public long getSequence() {
            return sequence;
        }

        @Override
        public String getThreadInfo() {
            return threadInfo;
        }

        @Override
        public StackTraceElement getLocation() {
            return location;
        }
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final String symbolicName;

        private TestBundle(long bundleId, String symbolicName) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
        }

        @Override
        public int getState() {
            return ACTIVE;
        }

        @Override
        public void start(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uninstall() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            return (Dictionary<String, String>) (Dictionary<?, ?>) new Properties();
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getLocation() {
            return symbolicName;
        }

        @Override
        public ServiceReference<?>[] getRegisteredServices() {
            return new ServiceReference<?>[0];
        }

        @Override
        public ServiceReference<?>[] getServicesInUse() {
            return new ServiceReference<?>[0];
        }

        @Override
        public boolean hasPermission(Object permission) {
            return true;
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Dictionary<String, String> getHeaders(String locale) {
            return getHeaders();
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
            return Collections.emptyEnumeration();
        }

        @Override
        public URL getEntry(String path) {
            return null;
        }

        @Override
        public long getLastModified() {
            return 0L;
        }

        @Override
        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            return Collections.emptyEnumeration();
        }

        @Override
        public BundleContext getBundleContext() {
            return null;
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            return Collections.emptyMap();
        }

        @Override
        public Version getVersion() {
            return Version.emptyVersion;
        }

        @Override
        public <A> A adapt(Class<A> type) {
            return null;
        }

        @Override
        public File getDataFile(String filename) {
            return null;
        }

        @Override
        public int compareTo(Bundle other) {
            return Long.compare(bundleId, other.getBundleId());
        }
    }

    private static final class TestServiceReference implements ServiceReference<Object> {
        private final String propertyKey;
        private final Object propertyValue;

        private TestServiceReference(String propertyKey, Object propertyValue) {
            this.propertyKey = propertyKey;
            this.propertyValue = propertyValue;
        }

        @Override
        public Object getProperty(String key) {
            if (propertyKey.equals(key)) {
                return propertyValue;
            }
            return null;
        }

        @Override
        public String[] getPropertyKeys() {
            return new String[] {propertyKey};
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return new Bundle[0];
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return true;
        }

        @Override
        public int compareTo(Object other) {
            return 0;
        }
    }
}
