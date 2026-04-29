/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_jansi.jansi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.fusesource.jansi.AnsiMain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiMainTest {

    @Test
    void readsBundledPomPropertiesVersion() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(AnsiMain.class, MethodHandles.lookup());
        MethodHandle getPomPropertiesVersion = lookup.findStatic(
                AnsiMain.class,
                "getPomPropertiesVersion",
                MethodType.methodType(String.class, String.class));

        String version = (String) getPomPropertiesVersion.invokeExact("org.fusesource.jansi/jansi");

        assertThat(version).isNotBlank();
    }
}
