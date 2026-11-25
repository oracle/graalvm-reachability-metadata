/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;


import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.naming.NamingContext;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingResourcesImplTest {
    @Test
    void closesSingletonResourceWhenNamingResourcesStop() throws Exception {
        StandardServer server = new StandardServer();
        NamingContext naming = new NamingContext(null, "cleanup");
        CloseableResource resource = new CloseableResource();
        naming.bind("catalog", resource);
        server.setGlobalNamingContext(naming);
        NamingResourcesImpl resources = server.getGlobalNamingResources();
        ContextResource descriptor = new ContextResource();
        descriptor.setName("catalog");
        descriptor.setType(CloseableResource.class.getName());
        descriptor.setSingleton(true);
        descriptor.setCloseMethod("close");
        resources.addResource(descriptor);
        try {
            resources.start();
            resources.stop();
            assertThat(resource.closed).isTrue();
        } finally {
            resources.destroy();
            naming.close();
        }
    }

    public static class CloseableResource implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
