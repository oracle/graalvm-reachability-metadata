/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoaderTest {
    @Test
    void runsAnExistingApplicationThroughExplicitParentDelegation() throws Throwable {
        Loader loader = new Loader(EntryPoint.class.getClassLoader(), null);
        loader.delegateLoadingOf(EntryPoint.class.getName());
        try {
            loader.run(new String[]{EntryPoint.class.getName(), "invoice", "42"});
            assertArrayEquals(new String[]{"invoice", "42"}, EntryPoint.arguments);
            loader.run(EntryPoint.class.getName(), new String[]{"receipt"});
            assertArrayEquals(new String[]{"receipt"}, EntryPoint.arguments);
            assertSame(EntryPoint.class, loader.loadClass(EntryPoint.class.getName()));
        } finally {
            EntryPoint.arguments = null;
        }
    }

    @Test
    void delegatesPlatformTypesAndReportsUnavailableClasses() throws Exception {
        Loader loader = new Loader(null, null);
        assertSame(String.class, loader.loadClass("java.lang.String"));
        assertSame(String.class, loader.loadClass("java.lang.String"));
        ClassNotFoundException failure = assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("example.UnavailableApplication"));
        assertEquals("example.UnavailableApplication", failure.getMessage());
    }

    public static class EntryPoint {
        private static String[] arguments;

        public static void main(String[] args) {
            arguments = args.clone();
        }
    }
}
