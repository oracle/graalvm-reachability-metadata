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

import static org.assertj.core.api.Assertions.assertThat;

public class ContextSelectorStaticBinderTest {

  @Test
  void shouldInstantiateConfiguredContextSelectorDynamically() throws Exception {
    String previousContextSelector = System.getProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    LoggerContext context = new LoggerContext();
    context.setName("dynamic-context-selector-test");

    try {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, DefaultContextSelector.class.getName());

      ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();
      binder.init(context, new Object());

      ContextSelector selector = binder.getContextSelector();
      assertThat(selector).isInstanceOf(DefaultContextSelector.class);
      assertThat(selector.getLoggerContext()).isSameAs(context);
      assertThat(selector.getContextNames()).containsExactly(context.getName());
    } finally {
      restoreContextSelector(previousContextSelector);
      context.stop();
    }
  }

  private void restoreContextSelector(String previousContextSelector) {
    if (previousContextSelector == null) {
      System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    } else {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, previousContextSelector);
    }
  }
}
