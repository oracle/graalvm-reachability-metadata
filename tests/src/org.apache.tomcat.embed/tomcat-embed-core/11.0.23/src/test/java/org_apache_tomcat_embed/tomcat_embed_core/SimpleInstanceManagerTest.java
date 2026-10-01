/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.tomcat.SimpleInstanceManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstanceManagerTest {

    @Test
    void constructsInstancesFromClassesAndNames() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();

        Object fromClass = manager.newInstance(ManagedComponent.class);
        Object fromContextLoader = manager.newInstance(ManagedComponent.class.getName());
        Object fromExplicitLoader = manager.newInstance(
                ManagedComponent.class.getName(), ManagedComponent.class.getClassLoader());

        assertThat(fromClass).isInstanceOf(ManagedComponent.class);
        assertThat(fromContextLoader).isInstanceOf(ManagedComponent.class);
        assertThat(fromExplicitLoader).isInstanceOf(ManagedComponent.class);
    }

    public static final class ManagedComponent {
        public ManagedComponent() {
        }
    }
}
