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

  @Test
  void shouldReadSelfDeclaredClassicVersionProperties() throws Throwable {
    String version = getVersionBySelfDeclaredProperties(ClassicConstants.class, "logback-classic");

    assertThat(version).isNotBlank();
    assertThat(version).isEqualTo(ClassicVersionUtil.getVersionBySelfDeclaredProperties());
  }

  private static String getVersionBySelfDeclaredProperties(Class<?> resourceAnchor, String moduleName) throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClassicVersionUtil.class, MethodHandles.lookup());
    MethodHandle versionReader = lookup.findStatic(
        ClassicVersionUtil.class,
        "getVersionBySelfDeclaredProperties",
        MethodType.methodType(String.class, Class.class, String.class));
    return (String) versionReader.invokeExact(resourceAnchor, moduleName);
  }
}
