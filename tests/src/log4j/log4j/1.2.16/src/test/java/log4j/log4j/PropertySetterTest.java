/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertySetterTest {

    @Test
    void convertsSupportedArgumentTypesThroughPropertySetterConversion() {
        TestablePropertySetter propertySetter = new TestablePropertySetter();

        assertThat(propertySetter.convert("text", String.class)).isEqualTo("text");
        assertThat(propertySetter.convert("WARN", Priority.class))
                .isSameAs(Level.WARN);
        assertThat(propertySetter.convert(TrackingErrorHandler.class.getName(), ErrorHandler.class))
                .isInstanceOf(TrackingErrorHandler.class);
    }

    @Test
    void instantiatesErrorHandlerPropertiesByClassName() {
        ErrorHandlerTarget target = new ErrorHandlerTarget();

        new PropertySetter(target).setProperty("errorHandler", TrackingErrorHandler.class.getName());

        assertThat(target.getErrorHandler()).isInstanceOf(TrackingErrorHandler.class);
    }

    @Test
    void configuresNestedOptionHandlerPropertiesThroughBeanSetters() {
        ConfigurableTarget target = new ConfigurableTarget();
        Properties properties = new Properties();
        properties.setProperty("target.handler", TrackingOptionHandler.class.getName());
        properties.setProperty("target.handler.label", "configured");
        properties.setProperty("target.handler.enabled", "true");

        new PropertySetter(target).setProperties(properties, "target.");

        assertThat(target.isActivated()).isTrue();
        assertThat(target.getHandler()).isInstanceOf(TrackingOptionHandler.class);

        TrackingOptionHandler handler = target.getHandler();
        assertThat(handler.getLabel()).isEqualTo("configured");
        assertThat(handler.isEnabled()).isTrue();
        assertThat(handler.isActivated()).isTrue();
    }

    public static final class ErrorHandlerTarget {
        private ErrorHandler errorHandler;

        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        public void setErrorHandler(ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
        }
    }

    public static final class ConfigurableTarget implements OptionHandler {
        private TrackingOptionHandler handler;
        private boolean activated;

        @Override
        public void activateOptions() {
            activated = true;
        }

        public TrackingOptionHandler getHandler() {
            return handler;
        }

        public void setHandler(TrackingOptionHandler handler) {
            this.handler = handler;
        }

        public boolean isActivated() {
            return activated;
        }
    }

    public static final class TestablePropertySetter extends PropertySetter {
        public TestablePropertySetter() {
            super(new Object());
        }

        public Object convert(String value, Class<?> type) {
            return super.convertArg(value, type);
        }
    }

    public static final class TrackingErrorHandler implements ErrorHandler {
        @Override
        public void setLogger(Logger logger) {
        }

        @Override
        public void error(String message, Exception e, int errorCode) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Exception e, int errorCode, LoggingEvent event) {
        }

        @Override
        public void setAppender(Appender appender) {
        }

        @Override
        public void setBackupAppender(Appender appender) {
        }

        @Override
        public void activateOptions() {
        }
    }

    public static final class TrackingOptionHandler implements OptionHandler {
        private String label;
        private boolean enabled;
        private boolean activated;

        @Override
        public void activateOptions() {
            activated = true;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isActivated() {
            return activated;
        }
    }
}
