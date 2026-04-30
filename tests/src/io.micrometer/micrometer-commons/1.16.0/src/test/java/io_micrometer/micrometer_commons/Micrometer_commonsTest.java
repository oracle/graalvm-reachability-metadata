/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_commons;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.docs.KeyName;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogLevel;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class Micrometer_commonsTest {
    @Test
    void keyValueFactoriesCreateComparableImmutableKeyValues() {
        KeyValue httpStatus = KeyValue.of("http.status", "200");
        KeyValue method = TestKeyName.METHOD.withValue("GET");
        SampleEndpoint endpoint = new SampleEndpoint("uri", "/api/orders");
        KeyValue uri = KeyValue.of(endpoint, SampleEndpoint::key, SampleEndpoint::value);

        assertThat(httpStatus.getKey()).isEqualTo("http.status");
        assertThat(httpStatus.getValue()).isEqualTo("200");
        assertThat(method).isEqualTo(KeyValue.of("method", "GET"));
        assertThat(uri).isEqualTo(KeyValue.of("uri", "/api/orders"));
        assertThat(Arrays.asList(uri, httpStatus, method).stream().sorted().map(KeyValue::getKey))
                .containsExactly("http.status", "method", "uri");
        assertThat(httpStatus.toString()).isEqualTo("keyValue(http.status=200)");
        assertThat(KeyValue.NONE_VALUE).isEqualTo("none");
    }

    @Test
    void keyValueFactoriesValidateInputsAndConvertAcceptedValues() {
        KeyValue priority = KeyValue.of("priority", 10, value -> value >= 1 && value <= 10);
        KeyValue userId = TestKeyName.USER.withValue("42",
                value -> value.toString().chars().allMatch(Character::isDigit));

        assertThat(priority.getKey()).isEqualTo("priority");
        assertThat(priority.getValue()).isEqualTo("10");
        assertThat(userId.getKey()).isEqualTo("user");
        assertThat(userId.getValue()).isEqualTo("42");
        assertThat(userId.toString()).isEqualTo("keyValue(user=42)");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KeyValue.of("priority", 11, value -> value >= 1 && value <= 10))
                .withMessageContaining("Argument [11]")
                .withMessageContaining("key [priority]");
        assertThatNullPointerException().isThrownBy(() -> KeyValue.of((String) null, "value"));
        assertThatNullPointerException().isThrownBy(() -> KeyValue.of("key", null));
    }

    @Test
    void keyValuesCreateSortedDistinctCollectionsFromVarargsAndIterables() {
        KeyValues keyValues = KeyValues.of("z", "last", "a", "first", "m", "middle", "a", "replacement");
        List<String> pairs = keyValues.stream()
                .map(keyValue -> keyValue.getKey() + "=" + keyValue.getValue())
                .collect(Collectors.toList());

        assertThat(pairs).containsExactly("a=replacement", "m=middle", "z=last");
        assertThat(keyValues).containsExactly(KeyValue.of("a", "replacement"), KeyValue.of("m", "middle"),
                KeyValue.of("z", "last"));
        assertThat(keyValues.toString()).isEqualTo("[keyValue(a=replacement),keyValue(m=middle),keyValue(z=last)]");
        assertThat(KeyValues.of(keyValues)).isSameAs(keyValues);
        assertThat(KeyValues.of(Collections.<KeyValue>emptyList())).isSameAs(KeyValues.empty());
        assertThat(KeyValues.of((Iterable<KeyValue>) null)).isSameAs(KeyValues.empty());
        assertThat(KeyValues.of((String[]) null)).isSameAs(KeyValues.empty());
        assertThat(KeyValues.of(new String[0])).isSameAs(KeyValues.empty());
        assertThat(KeyValues.of((String) null)).isSameAs(KeyValues.empty());
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> KeyValues.of("onlyKey"))
                .withMessage("size must be even, it is a set of key=value pairs");
    }

    @Test
    void keyValuesMergeAndConcatKeepKeysSortedAndLetLaterValuesWin() {
        KeyValues base = KeyValues.of("region", "us-east", "service", "checkout");
        KeyValues merged = base.and("service", "payments")
                .and(KeyValue.of("tenant", "acme"), KeyValue.of("region", "eu-west"));
        KeyValues concatenated = KeyValues.concat(merged, "zone", "blue", "service", "billing");

        assertThat(asPairs(merged)).containsExactly("region=eu-west", "service=payments", "tenant=acme");
        assertThat(asPairs(concatenated)).containsExactly("region=eu-west", "service=billing", "tenant=acme",
                "zone=blue");
        assertThat(base.and((Iterable<KeyValue>) null)).isSameAs(base);
        assertThat(base.and(KeyValues.empty())).isSameAs(base);
        assertThat(base.and((KeyValue[]) null)).isSameAs(base);
        assertThat(base.and(new KeyValue[0])).isSameAs(base);
        assertThat(KeyValues.concat(null, KeyValues.of("a", "b"))).containsExactly(KeyValue.of("a", "b"));
    }

    @Test
    void keyValuesMapDomainObjectsAndSupportIterationContracts() {
        List<SampleEndpoint> endpoints = Arrays.asList(new SampleEndpoint("uri", "/orders"),
                new SampleEndpoint("method", "POST"), new SampleEndpoint("status", "201"));
        KeyValues keyValues = KeyValues.of(endpoints, SampleEndpoint::key, SampleEndpoint::value)
                .and(Collections.singletonList(new SampleEndpoint("outcome", "SUCCESS")), SampleEndpoint::key,
                        SampleEndpoint::value);
        Iterator<KeyValue> iterator = keyValues.iterator();
        List<String> visited = new ArrayList<>();

        while (iterator.hasNext()) {
            KeyValue keyValue = iterator.next();
            visited.add(keyValue.getKey() + "=" + keyValue.getValue());
        }

        assertThat(visited).containsExactly("method=POST", "outcome=SUCCESS", "status=201", "uri=/orders");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove)
                .withMessage("cannot remove items from key values");
        assertThat(keyValues.spliterator().hasCharacteristics(java.util.Spliterator.ORDERED)).isTrue();
        assertThat(keyValues.spliterator().hasCharacteristics(java.util.Spliterator.SORTED)).isTrue();
    }

    @Test
    void keyValuesEqualityAndHashCodeAreBasedOnSortedKeyValueContent() {
        KeyValues first = KeyValues.of("b", "2", "a", "1");
        KeyValues second = KeyValues.of(KeyValue.of("a", "1"), KeyValue.of("b", "2"));
        KeyValues differentValue = KeyValues.of("a", "changed", "b", "2");
        KeyValues differentSize = KeyValues.of("a", "1");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(differentValue);
        assertThat(first).isNotEqualTo(differentSize);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("a=1,b=2");
    }

    @Test
    void keyNamesExposeDefaultsMergeArraysAndCreateKeyValues() {
        KeyName[] merged = KeyName.merge(new KeyName[]{TestKeyName.METHOD, TestKeyName.URI},
                new KeyName[]{OptionalKeyName.EXCEPTION});

        assertThat(Arrays.asList(merged))
                .containsExactly(TestKeyName.METHOD, TestKeyName.URI, OptionalKeyName.EXCEPTION);
        assertThat(TestKeyName.METHOD.asString()).isEqualTo("method");
        assertThat(TestKeyName.METHOD.isRequired()).isTrue();
        assertThat(OptionalKeyName.EXCEPTION.asString()).isEqualTo("exception");
        assertThat(OptionalKeyName.EXCEPTION.isRequired()).isFalse();
        assertThat(TestKeyName.URI.withValue("/actuator/health")).isEqualTo(KeyValue.of("uri", "/actuator/health"));
    }

    @Test
    void stringUtilsClassifiesBlankAndEmptyStrings() {
        assertThat(StringUtils.isEmpty(null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty(" ")).isFalse();
        assertThat(StringUtils.isNotEmpty("metrics")).isTrue();
        assertThat(StringUtils.isNotEmpty("")).isFalse();

        assertThat(StringUtils.isBlank(null)).isTrue();
        assertThat(StringUtils.isBlank("")).isTrue();
        assertThat(StringUtils.isBlank(" \t\n")).isTrue();
        assertThat(StringUtils.isBlank(" timers ")).isFalse();
        assertThat(StringUtils.isNotBlank("counter")).isTrue();
        assertThat(StringUtils.isNotBlank("\r\n")).isFalse();
    }

    @Test
    void stringUtilsTruncatesPlainlyOrWithIndicators() {
        assertThat(StringUtils.truncate("abcdef", 4)).isEqualTo("abcd");
        assertThat(StringUtils.truncate("abc", 4)).isEqualTo("abc");
        assertThat(StringUtils.truncate("abcdef", 5, "...")).isEqualTo("ab...");
        assertThat(StringUtils.truncate("abc", 5, "...")).isEqualTo("abc");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> StringUtils.truncate("abcdef", 3, "..."))
                .withMessage("maxLength must be greater than length of truncationIndicator");
    }

    @Test
    void valueResolverInterfacesCanBeComposedWithNoOpResolver() {
        ValueResolver noOpResolver = new NoOpValueResolver();
        ValueResolver upperCaseResolver = value -> value == null ? "missing" : value.toString().toUpperCase();
        ValueExpressionResolver expressionResolver = (expression, context) -> expression + ":"
                + Objects.toString(context);

        assertThat(noOpResolver.resolve("ignored")).isEmpty();
        assertThat(upperCaseResolver.resolve("timer")).isEqualTo("TIMER");
        assertThat(upperCaseResolver.resolve(null)).isEqualTo("missing");
        assertThat(expressionResolver.resolve("name", "jvm.memory.used")).isEqualTo("name:jvm.memory.used");
    }

    @Test
    void internalLoggerFactoryCreatesClassAndStringNamedLoggers() {
        InternalLoggerFactory previousFactory = InternalLoggerFactory.getDefaultFactory();
        RecordingLoggerFactory factory = new RecordingLoggerFactory(true);

        try {
            InternalLoggerFactory.setDefaultFactory(factory);

            InternalLogger classLogger = InternalLoggerFactory.getInstance(Micrometer_commonsTest.class);
            classLogger.info("class logger created");

            assertThat(classLogger.name()).isEqualTo(Micrometer_commonsTest.class.getName());
            assertThat(factory.logger.events).hasSize(1);
            assertThat(factory.logger.events.get(0).level).isEqualTo(InternalLogLevel.INFO);
            assertThat(factory.logger.events.get(0).message).isEqualTo("class logger created");

            InternalLogger namedLogger = InternalLoggerFactory.getInstance("micrometer.named");
            namedLogger.error("named logger created");

            assertThat(namedLogger.name()).isEqualTo("micrometer.named");
            assertThat(factory.logger.events).hasSize(2);
            assertThat(factory.logger.events.get(1).level).isEqualTo(InternalLogLevel.ERROR);
            assertThat(factory.logger.events.get(1).message).isEqualTo("named logger created");
            assertThatNullPointerException().isThrownBy(() -> InternalLoggerFactory.setDefaultFactory(null));
        } finally {
            InternalLoggerFactory.setDefaultFactory(previousFactory);
        }
    }

    @Test
    void warnThenDebugLoggerLogsInitialWarningAndSubsequentDebugMessages() {
        InternalLoggerFactory previousFactory = InternalLoggerFactory.getDefaultFactory();
        RecordingLoggerFactory factory = new RecordingLoggerFactory(true);
        IllegalStateException failure = new IllegalStateException("boom");

        try {
            InternalLoggerFactory.setDefaultFactory(factory);
            WarnThenDebugLogger logger = new WarnThenDebugLogger("micrometer.test");

            logger.log("metadata is incomplete");
            logger.log("metadata is still incomplete", failure);

            assertThat(factory.logger.name()).isEqualTo("micrometer.test");
            assertThat(factory.logger.events).hasSize(2);
            assertThat(factory.logger.events.get(0).level).isEqualTo(InternalLogLevel.WARN);
            assertThat(factory.logger.events.get(0).message)
                    .isEqualTo("metadata is incomplete Note that subsequent logs will be logged at debug level.");
            assertThat(factory.logger.events.get(0).throwable).isNull();
            assertThat(factory.logger.events.get(1).level).isEqualTo(InternalLogLevel.DEBUG);
            assertThat(factory.logger.events.get(1).message).isEqualTo("metadata is still incomplete");
            assertThat(factory.logger.events.get(1).throwable).isSameAs(failure);
        } finally {
            InternalLoggerFactory.setDefaultFactory(previousFactory);
        }
    }

    @Test
    void warnThenDebugLoggerSuppliersAreLazyWhenDebugIsDisabled() {
        InternalLoggerFactory previousFactory = InternalLoggerFactory.getDefaultFactory();
        RecordingLoggerFactory factory = new RecordingLoggerFactory(false);
        AtomicInteger supplierCalls = new AtomicInteger();

        try {
            InternalLoggerFactory.setDefaultFactory(factory);
            WarnThenDebugLogger logger = new WarnThenDebugLogger("micrometer.lazy");

            logger.log(() -> {
                supplierCalls.incrementAndGet();
                return "first warning";
            });
            logger.log(() -> {
                supplierCalls.incrementAndGet();
                return "hidden debug";
            });

            assertThat(supplierCalls).hasValue(1);
            assertThat(factory.logger.events).hasSize(1);
            assertThat(factory.logger.events.get(0).level).isEqualTo(InternalLogLevel.WARN);
            assertThat(factory.logger.events.get(0).message)
                    .isEqualTo("first warning Note that subsequent logs will be logged at debug level.");
        } finally {
            InternalLoggerFactory.setDefaultFactory(previousFactory);
        }
    }

    private static List<String> asPairs(KeyValues keyValues) {
        return keyValues.stream()
                .map(keyValue -> keyValue.getKey() + "=" + keyValue.getValue())
                .collect(Collectors.toList());
    }

    private enum TestKeyName implements KeyName {
        METHOD("method"), URI("uri"), USER("user");

        private final String key;

        TestKeyName(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }
    }

    private enum OptionalKeyName implements KeyName {
        EXCEPTION("exception");

        private final String key;

        OptionalKeyName(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }

        @Override
        public boolean isRequired() {
            return false;
        }
    }

    private static final class SampleEndpoint {
        private final String key;

        private final String value;

        private SampleEndpoint(String key, String value) {
            this.key = key;
            this.value = value;
        }

        private String key() {
            return key;
        }

        private String value() {
            return value;
        }
    }

    private static final class RecordingLoggerFactory extends InternalLoggerFactory {
        private final RecordingLogger logger;

        private RecordingLoggerFactory(boolean debugEnabled) {
            this.logger = new RecordingLogger(debugEnabled);
        }

        @Override
        protected InternalLogger newInstance(String name) {
            logger.name = name;
            return logger;
        }
    }

    private static final class RecordingLogger implements InternalLogger {
        private final boolean debugEnabled;

        private final List<LogEvent> events = new ArrayList<>();

        private String name;

        private RecordingLogger(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String message) {
            log(InternalLogLevel.TRACE, message);
        }

        @Override
        public void trace(String message, Object argument) {
            log(InternalLogLevel.TRACE, message, argument);
        }

        @Override
        public void trace(String message, Object firstArgument, Object secondArgument) {
            log(InternalLogLevel.TRACE, message, firstArgument, secondArgument);
        }

        @Override
        public void trace(String message, Object... arguments) {
            log(InternalLogLevel.TRACE, message, arguments);
        }

        @Override
        public void trace(String message, Throwable throwable) {
            log(InternalLogLevel.TRACE, message, throwable);
        }

        @Override
        public void trace(Throwable throwable) {
            log(InternalLogLevel.TRACE, throwable);
        }

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public void debug(String message) {
            log(InternalLogLevel.DEBUG, message);
        }

        @Override
        public void debug(String message, Object argument) {
            log(InternalLogLevel.DEBUG, message, argument);
        }

        @Override
        public void debug(String message, Object firstArgument, Object secondArgument) {
            log(InternalLogLevel.DEBUG, message, firstArgument, secondArgument);
        }

        @Override
        public void debug(String message, Object... arguments) {
            log(InternalLogLevel.DEBUG, message, arguments);
        }

        @Override
        public void debug(String message, Throwable throwable) {
            log(InternalLogLevel.DEBUG, message, throwable);
        }

        @Override
        public void debug(Throwable throwable) {
            log(InternalLogLevel.DEBUG, throwable);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
            log(InternalLogLevel.INFO, message);
        }

        @Override
        public void info(String message, Object argument) {
            log(InternalLogLevel.INFO, message, argument);
        }

        @Override
        public void info(String message, Object firstArgument, Object secondArgument) {
            log(InternalLogLevel.INFO, message, firstArgument, secondArgument);
        }

        @Override
        public void info(String message, Object... arguments) {
            log(InternalLogLevel.INFO, message, arguments);
        }

        @Override
        public void info(String message, Throwable throwable) {
            log(InternalLogLevel.INFO, message, throwable);
        }

        @Override
        public void info(Throwable throwable) {
            log(InternalLogLevel.INFO, throwable);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String message) {
            log(InternalLogLevel.WARN, message);
        }

        @Override
        public void warn(String message, Object argument) {
            log(InternalLogLevel.WARN, message, argument);
        }

        @Override
        public void warn(String message, Object... arguments) {
            log(InternalLogLevel.WARN, message, arguments);
        }

        @Override
        public void warn(String message, Object firstArgument, Object secondArgument) {
            log(InternalLogLevel.WARN, message, firstArgument, secondArgument);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            log(InternalLogLevel.WARN, message, throwable);
        }

        @Override
        public void warn(Throwable throwable) {
            log(InternalLogLevel.WARN, throwable);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
            log(InternalLogLevel.ERROR, message);
        }

        @Override
        public void error(String message, Object argument) {
            log(InternalLogLevel.ERROR, message, argument);
        }

        @Override
        public void error(String message, Object firstArgument, Object secondArgument) {
            log(InternalLogLevel.ERROR, message, firstArgument, secondArgument);
        }

        @Override
        public void error(String message, Object... arguments) {
            log(InternalLogLevel.ERROR, message, arguments);
        }

        @Override
        public void error(String message, Throwable throwable) {
            log(InternalLogLevel.ERROR, message, throwable);
        }

        @Override
        public void error(Throwable throwable) {
            log(InternalLogLevel.ERROR, throwable);
        }

        @Override
        public boolean isEnabled(InternalLogLevel level) {
            if (level == InternalLogLevel.TRACE) {
                return false;
            }
            return level != InternalLogLevel.DEBUG || debugEnabled;
        }

        @Override
        public void log(InternalLogLevel level, String message) {
            record(level, message, null);
        }

        @Override
        public void log(InternalLogLevel level, String message, Object argument) {
            record(level, message, null);
        }

        @Override
        public void log(InternalLogLevel level, String message, Object firstArgument, Object secondArgument) {
            record(level, message, null);
        }

        @Override
        public void log(InternalLogLevel level, String message, Object... arguments) {
            record(level, message, null);
        }

        @Override
        public void log(InternalLogLevel level, String message, Throwable throwable) {
            record(level, message, throwable);
        }

        @Override
        public void log(InternalLogLevel level, Throwable throwable) {
            record(level, "", throwable);
        }

        private void record(InternalLogLevel level, String message, Throwable throwable) {
            if (isEnabled(level)) {
                events.add(new LogEvent(level, message, throwable));
            }
        }
    }

    private static final class LogEvent {
        private final InternalLogLevel level;

        private final String message;

        private final Throwable throwable;

        private LogEvent(InternalLogLevel level, String message, Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
    }
}
