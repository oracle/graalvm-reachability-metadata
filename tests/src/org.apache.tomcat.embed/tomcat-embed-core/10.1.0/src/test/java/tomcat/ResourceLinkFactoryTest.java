/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;


import org.apache.naming.NamingContext;
import org.apache.naming.ResourceLinkRef;
import org.apache.naming.factory.ResourceLinkFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceLinkFactoryTest {
    @Test
    void resolvesAuthorizedGlobalResource() throws Exception {
        NamingContext global = new NamingContext(null, "global");
        global.bind("sharedGreeting", "hello");
        ResourceLinkFactory.setGlobalContext(global);
        ResourceLinkFactory.registerGlobalResourceAccess(global, "localGreeting", "sharedGreeting");
        try {
            ResourceLinkRef reference = new ResourceLinkRef(String.class.getName(), "sharedGreeting", null, null);
            assertThat(new ResourceLinkFactory().getObjectInstance(reference, null, null, null)).isEqualTo("hello");
        } finally {
            ResourceLinkFactory.deregisterGlobalResourceAccess(global);
            ResourceLinkFactory.setGlobalContext(null);
            global.close();
        }
    }
}
