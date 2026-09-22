/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Hashtable;

import org.apache.naming.NamingContext;
import org.apache.naming.ResourceLinkRef;
import org.apache.naming.factory.ResourceLinkFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceLinkFactoryTest {

    @Test
    void resolvesRegisteredGlobalResourceWithExpectedType() throws Exception {
        NamingContext globalContext = new NamingContext((Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties(), "global");
        globalContext.bind("sharedMessage", "configured-value");
        ResourceLinkFactory.setGlobalContext(globalContext);
        ResourceLinkFactory.registerGlobalResourceAccess(globalContext, "localMessage", "sharedMessage");
        ResourceLinkRef reference = new ResourceLinkRef(String.class.getName(), "sharedMessage", null, null);

        try {
            Object resource = new ResourceLinkFactory().getObjectInstance(reference, null, null, null);

            assertThat(resource).isEqualTo("configured-value");
        } finally {
            ResourceLinkFactory.deregisterGlobalResourceAccess(globalContext, "localMessage");
        }
    }
}
