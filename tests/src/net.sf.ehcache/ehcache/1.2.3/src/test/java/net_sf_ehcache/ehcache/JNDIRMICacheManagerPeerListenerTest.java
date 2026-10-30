/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import javax.naming.Context;
import javax.naming.InitialContext;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.CachePeer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class JNDIRMICacheManagerPeerListenerTest {
    @TempDir
    Path directory;

    @Test
    @ResourceLock("jndi-properties")
    void bindsDistributedCacheInNamingContext() throws Exception {
        try (JndiFixture fixture = new JndiFixture(directory)) {
            Context context = new InitialContext();
            try {
                CachePeer remote = (CachePeer) context.lookup("replicated");
                remote.put(new Element("listener", "bound-value"));
                assertEquals("bound-value", fixture.cache.get("listener").getObjectValue());
                assertEquals(1, fixture.listener.getBoundCachePeers().size());
                assertEquals(fixture.cache.getGuid(), remote.getGuid());
            } finally {
                context.close();
            }
        }
    }
}
