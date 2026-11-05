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
  @ResourceLock("logback.ContextSelector")
  void selectsTheConfiguredContextByName() throws Exception {
    String property = ClassicConstants.LOGBACK_CONTEXT_SELECTOR;
    String previous = System.getProperty(property);
    LoggerContext context = new LoggerContext();
    try {
      context.setName("selected-context");
      System.setProperty(property, DefaultContextSelector.class.getName());
      ContextSelectorStaticBinder binder = new ContextSelectorStaticBinder();
      binder.init(context, new Object());

      ContextSelector selector = binder.getContextSelector();
      assertThat(selector).isInstanceOf(DefaultContextSelector.class);
      assertThat(selector.getLoggerContext()).isSameAs(context);
      assertThat(selector.getDefaultLoggerContext()).isSameAs(context);
      assertThat(selector.getLoggerContext("selected-context")).isSameAs(context);
      assertThat(selector.getLoggerContext("unknown-context")).isNull();
      assertThat(selector.getContextNames()).containsExactly("selected-context");
    } finally {
      if (previous == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previous);
      }
      context.stop();
    }
  }
}
