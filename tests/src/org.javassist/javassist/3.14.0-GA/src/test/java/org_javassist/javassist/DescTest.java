/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.runtime.Desc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DescTest {
    @Test
    void resolvesMethodDescriptorsUsingBothDocumentedLookupModes() {
        boolean previous = Desc.useContextClassLoader;
        try {
            for (boolean contextLoader : new boolean[]{false, true}) {
                Desc.useContextClassLoader = contextLoader;
                assertEquals(TestSupport.Account.class, Desc.getClazz(TestSupport.Account.class.getName()));
                assertEquals(String.class, Desc.getType("Ljava/lang/String;"));
                assertArrayEquals(new Class<?>[]{String.class, int.class, boolean.class, long.class},
                        Desc.getParams("(Ljava/lang/String;IZJ)V"));
                assertEquals(void.class, Desc.getType("V"));
            }
            Desc.useContextClassLoader = false;
            assertEquals(String[][].class, Desc.getType("[[Ljava/lang/String;"));
            assertEquals(int[].class, Desc.getType("[I"));
            assertArrayEquals(new Class<?>[]{String.class, int[].class},
                    Desc.getParams("(Ljava/lang/String;[I)V"));
        } finally {
            Desc.useContextClassLoader = previous;
        }
    }
}
