/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.selector.ContextSelector;
import ch.qos.logback.classic.selector.DefaultContextSelector;
import ch.qos.logback.classic.util.ContextSelectorStaticBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextSelectorStaticBinderTest {

  @Test
  @ResourceLock("logback.contextSelector")
  void initUsesDefaultContextSelectorWhenNoSelectorIsConfigured() throws Exception {
    String previousContextSelector = System.getProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    LoggerContext loggerContext = new LoggerContext();
    try {
      System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
      ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();
      binder.init(loggerContext, new Object());

      ContextSelector contextSelector = binder.getContextSelector();
      assertThat(contextSelector).isExactlyInstanceOf(DefaultContextSelector.class);
      assertThat(contextSelector.getDefaultLoggerContext()).isSameAs(loggerContext);
    } finally {
      restoreContextSelectorProperty(previousContextSelector);
      loggerContext.stop();
    }
  }

  private void restoreContextSelectorProperty(String previousContextSelector) {
    if (previousContextSelector == null) {
      System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    } else {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, previousContextSelector);
    }
  }
}
