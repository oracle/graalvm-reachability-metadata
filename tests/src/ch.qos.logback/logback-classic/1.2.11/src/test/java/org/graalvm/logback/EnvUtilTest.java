/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.util.EnvUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvUtilTest {

  @Test
  void reportsThatGroovyIsAvailableWithTheOptionalDependency() {
    assertThat(EnvUtil.isGroovyAvailable()).isTrue();
  }
}
