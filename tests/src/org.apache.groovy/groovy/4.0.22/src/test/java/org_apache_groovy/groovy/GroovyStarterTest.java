/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.tools.GroovyStarter;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroovyStarterTest {
    @Test
    void launchesAnAlreadyCompiledMainClassWithItsArguments() throws Exception {
        Application.received.set(null);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            GroovyStarter.rootLoader(new String[]{"--main", Application.class.getName(), "order", "17"});
            assertEquals(List.of("order", "17"), Application.received.get());
        } finally {
            ClassLoader current = thread.getContextClassLoader();
            thread.setContextClassLoader(previous);
            if (current != previous && current instanceof RootLoader) {
                ((RootLoader) current).close();
            }
        }
    }

    public static class Application {
        public static final AtomicReference<List<String>> received = new AtomicReference<>();

        public static void main(String[] arguments) {
            received.set(List.of(arguments));
        }
    }
}
