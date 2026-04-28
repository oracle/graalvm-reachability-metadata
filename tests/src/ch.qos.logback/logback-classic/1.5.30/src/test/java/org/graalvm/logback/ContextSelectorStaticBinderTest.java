/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.selector.DefaultContextSelector;
import ch.qos.logback.classic.util.ContextSelectorStaticBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextSelectorStaticBinderTest {

  private final LoggerContext loggerContext = new LoggerContext();

  @Test
  void configuredContextSelectorClassIsLoadedAndInstantiated() throws Exception {
    String previousContextSelector = System.getProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    try {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, DefaultContextSelector.class.getName());
      ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();

      binder.init(loggerContext, new Object());

      assertThat(binder.getContextSelector()).isInstanceOf(DefaultContextSelector.class);
      assertThat(binder.getContextSelector().getDefaultLoggerContext()).isSameAs(loggerContext);
    } finally {
      restoreContextSelectorProperty(previousContextSelector);
    }
  }

  private void restoreContextSelectorProperty(String previousContextSelector) {
    if (previousContextSelector == null) {
      System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    } else {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, previousContextSelector);
    }
  }

  @AfterEach
  void tearDown() {
    loggerContext.stop();
  }
}
