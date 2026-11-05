/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import javax.servlet.ServletContextEvent;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.constructs.web.ShutdownListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
@Isolated
public class ShutdownListenerTest {
    @TempDir
    Path directory;

    @Test
    void shutsDownAllManagersWhenTheWebApplicationIsDestroyed() {
        CacheManager first = TestSupport.manager(directory.resolve("first"));
        try {
            CacheManager second = TestSupport.manager(directory.resolve("second"));
            try {
                first.addCache("orders");
                second.addCache("users");
                first.getCache("orders").put(new Element("order", "created"));
                ShutdownListener listener = new ShutdownListener();
                ServletContextEvent event = new ServletContextEvent(new WebContext());
                listener.contextInitialized(event);
                assertEquals("created", first.getCache("orders").get("order").getObjectValue());
                assertEquals(Status.STATUS_ALIVE, second.getStatus());

                listener.contextDestroyed(event);
                assertEquals(Status.STATUS_SHUTDOWN, first.getStatus());
                assertEquals(Status.STATUS_SHUTDOWN, second.getStatus());
                assertTrue(CacheManager.ALL_CACHE_MANAGERS.isEmpty());
            } finally {
                second.shutdown();
            }
        } finally {
            first.shutdown();
        }
    }
}
