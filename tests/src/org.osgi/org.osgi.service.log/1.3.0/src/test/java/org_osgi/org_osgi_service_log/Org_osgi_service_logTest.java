/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

public class Org_osgi_service_logTest {
    @Test
    void logLevelsExposeSpecifiedSeverityValues() {
        assertThat(LogService.LOG_ERROR).isEqualTo(1);
        assertThat(LogService.LOG_WARNING).isEqualTo(2);
        assertThat(LogService.LOG_INFO).isEqualTo(3);
        assertThat(LogService.LOG_DEBUG).isEqualTo(4);
        assertThat(List.of(
                LogService.LOG_ERROR,
                LogService.LOG_WARNING,
                LogService.LOG_INFO,
                LogService.LOG_DEBUG)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void loggingOverloadsCreateEntriesWithExpectedOptionalFields() {
        InMemoryLogService logService = new InMemoryLogService();
        TestServiceReference reference = new TestServiceReference("service.id", 7L);
        IllegalStateException exception = new IllegalStateException("not available");
        long before = System.currentTimeMillis();

        logService.log(LogService.LOG_INFO, "started");
        logService.log(LogService.LOG_WARNING, "warning", exception);
        logService.log(reference, LogService.LOG_DEBUG, "service debug");
        logService.log(reference, LogService.LOG_ERROR, "service failed", exception);
        logService.log(99, null, null);
        long after = System.currentTimeMillis();

        List<LogEntry> entries = logService.entriesInMostRecentFirstOrder();
        assertThat(entries).hasSize(5);

        LogEntry nullPayload = entries.get(0);
        assertThat(nullPayload.getLevel()).isEqualTo(99);
        assertThat(nullPayload.getMessage()).isNull();
        assertThat(nullPayload.getException()).isNull();
        assertThat(nullPayload.getServiceReference()).isNull();

        LogEntry serviceFailure = entries.get(1);
        assertThat(serviceFailure.getLevel()).isEqualTo(LogService.LOG_ERROR);
        assertThat(serviceFailure.getMessage()).isEqualTo("service failed");
        assertThat(serviceFailure.getException()).isSameAs(exception);
        assertThat(serviceFailure.getServiceReference()).isSameAs(reference);
        assertThat(serviceFailure.getBundle()).isNull();
        assertThat(serviceFailure.getTime()).isBetween(before, after);

        LogEntry serviceDebug = entries.get(2);
        assertThat(serviceDebug.getLevel()).isEqualTo(LogService.LOG_DEBUG);
        assertThat(serviceDebug.getMessage()).isEqualTo("service debug");
        assertThat(serviceDebug.getException()).isNull();
        assertThat(serviceDebug.getServiceReference()).isSameAs(reference);

        LogEntry warning = entries.get(3);
        assertThat(warning.getLevel()).isEqualTo(LogService.LOG_WARNING);
        assertThat(warning.getMessage()).isEqualTo("warning");
        assertThat(warning.getException()).isSameAs(exception);
        assertThat(warning.getServiceReference()).isNull();

        LogEntry started = entries.get(4);
        assertThat(started.getLevel()).isEqualTo(LogService.LOG_INFO);
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

        logService.log(LogService.LOG_INFO, "first");
        logService.log(LogService.LOG_WARNING, "second");
        logService.removeLogListener(firstListener);
        logService.log(LogService.LOG_ERROR, "third");

        assertThat(firstListenerEntries).extracting(LogEntry::getMessage).containsExactly("first", "second");
        assertThat(secondListenerEntries).extracting(LogEntry::getMessage).containsExactly("first", "second", "third");
    }

    @Test
    void logReaderReturnsMostRecentEntriesInAnEnumerationSnapshot() {
        InMemoryLogService logService = new InMemoryLogService();
        LogReaderService readerService = logService;

        logService.log(LogService.LOG_INFO, "oldest");
        logService.log(LogService.LOG_WARNING, "middle");
        logService.log(LogService.LOG_ERROR, "newest");

        Enumeration logSnapshot = readerService.getLog();
        logService.log(LogService.LOG_DEBUG, "after snapshot");

        List<String> messages = new ArrayList<>();
        while (logSnapshot.hasMoreElements()) {
            messages.add(((LogEntry) logSnapshot.nextElement()).getMessage());
        }

        assertThat(messages).containsExactly("newest", "middle", "oldest");
        assertThat(logService.entriesInMostRecentFirstOrder())
                .extracting(LogEntry::getMessage)
                .containsExactly("after snapshot", "newest", "middle", "oldest");
    }

    private static final class InMemoryLogService implements LogService, LogReaderService {
        private final List<LogEntry> entries = new ArrayList<>();
        private final List<LogListener> listeners = new ArrayList<>();

        @Override
        public void log(int level, String message) {
            log(null, level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            log(null, level, message, exception);
        }

        @Override
        public void log(ServiceReference sr, int level, String message) {
            log(sr, level, message, null);
        }

        @Override
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            LogEntry entry = new SimpleLogEntry(null, sr, level, message, exception, System.currentTimeMillis());
            entries.add(0, entry);
            for (LogListener listener : new ArrayList<>(listeners)) {
                listener.logged(entry);
            }
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
        public Enumeration getLog() {
            return Collections.enumeration(new ArrayList<>(entries));
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

    private static final class SimpleLogEntry implements LogEntry {
        private final Bundle bundle;
        private final ServiceReference serviceReference;
        private final int level;
        private final String message;
        private final Throwable exception;
        private final long time;

        private SimpleLogEntry(Bundle bundle, ServiceReference serviceReference, int level, String message,
                Throwable exception, long time) {
            this.bundle = bundle;
            this.serviceReference = serviceReference;
            this.level = level;
            this.message = message;
            this.exception = exception;
            this.time = time;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public ServiceReference getServiceReference() {
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
            return new String[] { propertyKey };
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
