/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvUtilTest {

  @Test
  void configurationEnablesGroovyFrameworkPackagesWhenGroovyIsAvailable() throws Exception {
    LoggerContext context = new LoggerContext();
    try {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(new ByteArrayInputStream("""
          <configuration>
            <logger name="configured.logger" level="DEBUG"/>
          </configuration>
          """.getBytes(StandardCharsets.UTF_8)));

      assertThat(context.getLogger("configured.logger").getLevel()).isEqualTo(Level.DEBUG);
      assertThat(context.getFrameworkPackages()).contains("org.codehaus.groovy.runtime");
    } finally {
      context.stop();
    }
  }
}
