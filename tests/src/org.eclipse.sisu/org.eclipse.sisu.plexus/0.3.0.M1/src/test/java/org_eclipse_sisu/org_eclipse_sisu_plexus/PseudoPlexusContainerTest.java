/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.sisu.plexus.PlexusSpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.junit.jupiter.api.Test;

public class PseudoPlexusContainerTest {
    @Test
    void looksUpBoundServicesByStringRole() throws Exception {
        Greeting greeting = new FixedGreeting();
        Injector injector = Guice.createInjector(new WireModule(
                new PlexusSpaceModule(new URLClassSpace(getClass().getClassLoader())),
                binder -> {
                    binder.bind(LoggerManager.class).toInstance(new ConsoleLoggerManager());
                    binder.bind(Greeting.class).annotatedWith(Names.named("default")).toInstance(greeting);
                }));
        PlexusContainer container = injector.getInstance(PlexusContainer.class);
        try {
            String role = Greeting.class.getName();

            assertTrue(container.hasComponent(role));
            Greeting found = (Greeting) container.lookup(role);
            assertSame(greeting, found);
            assertEquals("hello from a bound service", found.message());
            assertEquals(List.of(greeting), container.lookupList(role));
            assertEquals(Map.of("default", greeting), container.lookupMap(role));
        } finally {
            container.dispose();
        }
    }

    public interface Greeting {
        String message();
    }

    public static class FixedGreeting implements Greeting {
        @Override
        public String message() {
            return "hello from a bound service";
        }
    }
}
