/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class GrammarTrexLocalizerDynamicAccessTest {
    private static final String LOCALIZER_CLASS_NAME = "com.ctc.wstx.shaded.msv_core.grammar.trex.Localizer";
    private static final MethodHandle LOCALIZE_ONE_ARGUMENT = localizeOneArgumentHandle();

    @Test
    void localizesTrexGrammarMessagesFromResourceBundles() throws Throwable {
        String message = (String) LOCALIZE_ONE_ARGUMENT.invokeExact("TypedString.Diagnosis", (Object) "expected literal");

        assertThat(message)
                .contains("value must be")
                .contains("expected literal");
    }

    private static MethodHandle localizeOneArgumentHandle() {
        try {
            Class<?> localizerClass = Class.forName(LOCALIZER_CLASS_NAME);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(localizerClass, MethodHandles.lookup());
            return lookup.findStatic(
                    localizerClass,
                    "localize",
                    MethodType.methodType(String.class, String.class, Object.class)
            );
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }
}
