/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagerBaseTest {

    @Test
    void createsConfiguredSessionIdGeneratorOnDemand() {
        ConfigurableManager manager = new ConfigurableManager();
        manager.useGenerator(CustomSessionIdGenerator.class);

        SessionIdGenerator generator = manager.getSessionIdGenerator();

        assertThat(generator).isInstanceOf(CustomSessionIdGenerator.class);
        assertThat(manager.getSessionIdGenerator()).isSameAs(generator);
    }

    public static class CustomSessionIdGenerator extends StandardSessionIdGenerator {
    }

    public static class ConfigurableManager extends StandardManager {
        public void useGenerator(Class<? extends SessionIdGenerator> generatorClass) {
            sessionIdGenerator = null;
            sessionIdGeneratorClass = generatorClass;
        }
    }
}
