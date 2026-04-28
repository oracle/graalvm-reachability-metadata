/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.util.ClassicVersionUtil;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ClassicVersionUtilTest {

  private static final String CLASSIC_MODULE_NAME = "logback-classic";
  private static final MethodType VERSION_LOOKUP_METHOD_TYPE = MethodType.methodType(String.class, Class.class,
          String.class);

  @Test
  void publicVersionLookupReadsSelfDeclaredProperties() {
    assertThat(ClassicVersionUtil.getVersionBySelfDeclaredProperties()).isNotBlank();
  }

  @Test
  void packageScopedVersionLookupReadsResourceBesideSuppliedClass() throws Throwable {
    MethodHandle versionLookup = MethodHandles.privateLookupIn(ClassicVersionUtil.class, MethodHandles.lookup())
            .findStatic(ClassicVersionUtil.class, "getVersionBySelfDeclaredProperties", VERSION_LOOKUP_METHOD_TYPE);

    String version = (String) versionLookup.invokeExact(ClassicConstants.class, CLASSIC_MODULE_NAME);

    assertThat(version).isEqualTo(ClassicVersionUtil.getVersionBySelfDeclaredProperties());
  }
}
