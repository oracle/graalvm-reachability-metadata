/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropertyConfiguratorTest {

    private static final String ROOT_LOGGER_NAME = "";
    private static final String INJECTED_LOGGER_NAME = "org.jboss.logmanager.injected";

    @BeforeEach
    @AfterEach
    void resetRootLogger() {
        Logger rootLogger = LogContext.getSystemLogContext().getLogger(ROOT_LOGGER_NAME);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
            handler.close();
        }
        rootLogger.setFilter(null);
        rootLogger.setUseParentHandlers(true);
    }

    @Test
    void configuresHandlersFiltersFormattersAndErrorManagersFromProperties() throws Exception {
        String properties = """
                reportErrors=true
                logger.level=INFO
                logger.handlers=mainHandler
                logger.filter=rootFilter
                logger.useParentHandlers=false
                handler.mainHandler=%s
                handler.mainHandler.level=WARNING
                handler.mainHandler.encoding=UTF-8
                handler.mainHandler.errorManager=mainErrorManager
                handler.mainHandler.filter=handlerFilter
                handler.mainHandler.formatter=mainFormatter
                handler.mainHandler.properties=name,enabledFlag,byteValue,shortValue,intValue,longValue,floatValue,doubleValue,charValue,timeZone,charset,injectedLevel,injectedLogger,nestedHandler,nestedFilter,nestedFormatter,mode
                handler.mainHandler.name=primary
                handler.mainHandler.enabledFlag=true
                handler.mainHandler.byteValue=7
                handler.mainHandler.shortValue=12
                handler.mainHandler.intValue=42
                handler.mainHandler.longValue=123456789
                handler.mainHandler.floatValue=1.25
                handler.mainHandler.doubleValue=2.5
                handler.mainHandler.charValue=Q
                handler.mainHandler.timeZone=UTC
                handler.mainHandler.charset=UTF-8
                handler.mainHandler.injectedLevel=INFO
                handler.mainHandler.injectedLogger=%s
                handler.mainHandler.nestedHandler=childHandler
                handler.mainHandler.nestedFilter=handlerFilter
                handler.mainHandler.nestedFormatter=mainFormatter
                handler.mainHandler.mode=ADVANCED
                handler.childHandler=%s
                handler.childHandler.properties=name
                handler.childHandler.name=child
                filter.rootFilter=%s
                filter.rootFilter.properties=enabled,thresholdChar
                filter.rootFilter.enabled=true
                filter.rootFilter.thresholdChar=A
                filter.handlerFilter=%s
                filter.handlerFilter.properties=enabled,thresholdChar
                filter.handlerFilter.enabled=true
                filter.handlerFilter.thresholdChar=H
                formatter.mainFormatter=%s
                formatter.mainFormatter.properties=pattern
                formatter.mainFormatter.pattern=formatted-%s
                errorManager.mainErrorManager=%s
                errorManager.mainErrorManager.properties=label
                errorManager.mainErrorManager.label=errors
                """.formatted(
                ConfigurableHandler.class.getName(),
                INJECTED_LOGGER_NAME,
                SecondaryHandler.class.getName(),
                ConfigurableFilter.class.getName(),
                ConfigurableFilter.class.getName(),
                ConfigurableFormatter.class.getName(),
                "%s",
                ConfigurableErrorManager.class.getName()
        );

        PropertyConfigurator configurator = new PropertyConfigurator();
        configurator.configure(new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        Logger rootLogger = LogContext.getSystemLogContext().getLogger(ROOT_LOGGER_NAME);
        assertThat(rootLogger.getFilter()).isInstanceOf(ConfigurableFilter.class);
        assertThat(rootLogger.getUseParentHandlers()).isFalse();
        assertThat(rootLogger.getHandlers()).hasSize(1);

        ConfigurableFilter rootFilter = (ConfigurableFilter) rootLogger.getFilter();
        assertThat(rootFilter.enabled).isTrue();
        assertThat(rootFilter.thresholdChar).isEqualTo('A');

        ConfigurableHandler handler = (ConfigurableHandler) rootLogger.getHandlers()[0];
        assertThat(handler.getLevel().getName()).isEqualTo("WARNING");
        assertThat(handler.getEncoding()).isEqualTo("UTF-8");
        assertThat(handler.name).isEqualTo("primary");
        assertThat(handler.enabledFlag).isTrue();
        assertThat(handler.byteValue).isEqualTo((byte) 7);
        assertThat(handler.shortValue).isEqualTo((short) 12);
        assertThat(handler.intValue).isEqualTo(42);
        assertThat(handler.longValue).isEqualTo(123456789L);
        assertThat(handler.floatValue).isEqualTo(1.25f);
        assertThat(handler.doubleValue).isEqualTo(2.5d);
        assertThat(handler.charValue).isEqualTo('Q');
        assertThat(handler.timeZone).isEqualTo(TimeZone.getTimeZone("UTC"));
        assertThat(handler.charset).isEqualTo(StandardCharsets.UTF_8);
        assertThat(handler.injectedLevel.getName()).isEqualTo("INFO");
        assertThat(handler.injectedLogger.getName()).isEqualTo(INJECTED_LOGGER_NAME);
        assertThat(handler.mode).isEqualTo(TestMode.ADVANCED);

        assertThat(handler.getErrorManager()).isInstanceOf(ConfigurableErrorManager.class);
        ConfigurableErrorManager errorManager = (ConfigurableErrorManager) handler.getErrorManager();
        assertThat(errorManager.label).isEqualTo("errors");

        assertThat(handler.getFilter()).isInstanceOf(ConfigurableFilter.class);
        ConfigurableFilter handlerFilter = (ConfigurableFilter) handler.getFilter();
        assertThat(handlerFilter.enabled).isTrue();
        assertThat(handlerFilter.thresholdChar).isEqualTo('H');

        assertThat(handler.getFormatter()).isInstanceOf(ConfigurableFormatter.class);
        ConfigurableFormatter formatter = (ConfigurableFormatter) handler.getFormatter();
        assertThat(formatter.pattern).isEqualTo("formatted-%s");

        assertThat(handler.nestedHandler).isInstanceOf(SecondaryHandler.class);
        SecondaryHandler nestedHandler = (SecondaryHandler) handler.nestedHandler;
        assertThat(nestedHandler.name).isEqualTo("child");
        assertThat(handler.nestedFilter).isSameAs(handlerFilter);
        assertThat(handler.nestedFormatter).isSameAs(formatter);

        rootLogger.warning("Hello from PropertyConfigurator");
        assertThat(handler.publishedMessages).containsExactly("formatted-Hello from PropertyConfigurator");
    }

    enum TestMode {
        BASIC,
        ADVANCED
    }

    public static class ConfigurableHandler extends Handler {
        String name;
        boolean enabledFlag;
        byte byteValue;
        short shortValue;
        int intValue;
        long longValue;
        float floatValue;
        double doubleValue;
        char charValue;
        TimeZone timeZone;
        Charset charset;
        java.util.logging.Level injectedLevel;
        java.util.logging.Logger injectedLogger;
        Handler nestedHandler;
        Filter nestedFilter;
        Formatter nestedFormatter;
        TestMode mode;
        final java.util.List<String> publishedMessages = new java.util.ArrayList<>();

        public void setName(String name) {
            this.name = name;
        }

        public void setEnabledFlag(boolean enabledFlag) {
            this.enabledFlag = enabledFlag;
        }

        public void setByteValue(byte byteValue) {
            this.byteValue = byteValue;
        }

        public void setShortValue(short shortValue) {
            this.shortValue = shortValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public void setCharValue(char charValue) {
            this.charValue = charValue;
        }

        public void setTimeZone(TimeZone timeZone) {
            this.timeZone = timeZone;
        }

        public void setCharset(Charset charset) {
            this.charset = charset;
        }

        public void setInjectedLevel(java.util.logging.Level injectedLevel) {
            this.injectedLevel = injectedLevel;
        }

        public void setInjectedLogger(java.util.logging.Logger injectedLogger) {
            this.injectedLogger = injectedLogger;
        }

        public void setNestedHandler(Handler nestedHandler) {
            this.nestedHandler = nestedHandler;
        }

        public void setNestedFilter(Filter nestedFilter) {
            this.nestedFilter = nestedFilter;
        }

        public void setNestedFormatter(Formatter nestedFormatter) {
            this.nestedFormatter = nestedFormatter;
        }

        public void setMode(TestMode mode) {
            this.mode = mode;
        }

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            publishedMessages.add(getFormatter().format(record));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    public static class SecondaryHandler extends Handler {
        String name;

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    public static class ConfigurableFilter implements Filter {
        boolean enabled;
        char thresholdChar;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setThresholdChar(char thresholdChar) {
            this.thresholdChar = thresholdChar;
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            return enabled && !record.getMessage().isEmpty() && record.getMessage().charAt(0) >= thresholdChar;
        }
    }

    public static class ConfigurableFormatter extends Formatter {
        String pattern;

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public String format(LogRecord record) {
            return pattern.formatted(record.getMessage());
        }
    }

    public static class ConfigurableErrorManager extends ErrorManager {
        String label;

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
