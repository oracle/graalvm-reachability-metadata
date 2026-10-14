/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.tomcat.SimpleInstanceManager;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstanceManagerTest {

    @Test
    void createsInstancesFromClassesAndClassNames() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();
        String implementationName = JSSEImplementation.class.getName();

        Object fromClass = manager.newInstance(JSSEImplementation.class);
        Object fromContextLoader = manager.newInstance(implementationName);
        Object fromExplicitLoader = manager.newInstance(implementationName, JSSEImplementation.class.getClassLoader());

        assertThat(fromClass).isInstanceOf(SSLImplementation.class);
        assertThat(fromContextLoader).isInstanceOf(SSLImplementation.class);
        assertThat(fromExplicitLoader).isInstanceOf(SSLImplementation.class);
    }
}
