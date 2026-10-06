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
  void createsTheSelectorNamedByTheConfigurationProperty() throws Exception {
    String previousValue = System.getProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
    LoggerContext context = new LoggerContext();
    try {
      System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, DefaultContextSelector.class.getName());
      ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();

      binder.init(context, new Object());

      ContextSelector selector = binder.getContextSelector();
      assertThat(selector).isExactlyInstanceOf(DefaultContextSelector.class);
      assertThat(selector.getDefaultLoggerContext()).isSameAs(context);
    } finally {
      if (previousValue == null) {
        System.clearProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
      } else {
        System.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, previousValue);
      }
      context.stop();
    }
  }
}
