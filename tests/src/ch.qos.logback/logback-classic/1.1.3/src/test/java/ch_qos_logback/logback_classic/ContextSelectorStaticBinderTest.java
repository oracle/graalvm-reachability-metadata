/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.selector.ContextSelector;
import ch.qos.logback.classic.util.ContextSelectorStaticBinder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ContextSelectorStaticBinderTest {

    @AfterEach
    void clearContextSelectorProperty() {
        System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    }

    @Test
    void initializesTheConfiguredContextSelectorImplementation() throws Exception {
        LoggerContext context = new LoggerContext();
        ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();
        System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, TestContextSelector.class.getName());

        binder.init(context, new Object());

        assertThat(binder.getContextSelector()).isInstanceOf(TestContextSelector.class);
        assertThat(binder.getContextSelector().getLoggerContext()).isSameAs(context);
    }

    public static final class TestContextSelector implements ContextSelector {

        private final LoggerContext loggerContext;

        public TestContextSelector(LoggerContext loggerContext) {
            this.loggerContext = loggerContext;
        }

        @Override
        public LoggerContext getLoggerContext() {
            return loggerContext;
        }

        @Override
        public LoggerContext getLoggerContext(String name) {
            return Objects.equals(loggerContext.getName(), name) ? loggerContext : null;
        }

        @Override
        public LoggerContext getDefaultLoggerContext() {
            return loggerContext;
        }

        @Override
        public LoggerContext detachLoggerContext(String loggerContextName) {
            return null;
        }

        @Override
        public List<String> getContextNames() {
            return Collections.singletonList(loggerContext.getName());
        }
    }
}
