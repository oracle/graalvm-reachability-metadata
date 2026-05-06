/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.jansi.AnsiMain;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiMainTest {

    @Test
    void readsBundledJansiPomPropertiesVersion() throws Throwable {
        MethodHandle getPomPropertiesVersion = MethodHandles.privateLookupIn(AnsiMain.class, MethodHandles.lookup())
                .findStatic(AnsiMain.class, "getPomPropertiesVersion",
                        MethodType.methodType(String.class, String.class));

        String version = (String) getPomPropertiesVersion.invokeExact("org.jline/jansi");

        assertThat(version).isNotBlank();
    }
}
