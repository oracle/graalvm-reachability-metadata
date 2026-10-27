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
    void recreatesConfiguredSessionIdGeneratorWhenAnInstanceIsNeeded() {
        RestartableManager manager = new RestartableManager();
        StandardSessionIdGenerator configuredGenerator = new StandardSessionIdGenerator();
        configuredGenerator.setSessionIdLength(20);
        manager.setSessionIdGenerator(configuredGenerator);

        manager.releaseGeneratorInstance();
        SessionIdGenerator recreatedGenerator = manager.getSessionIdGenerator();

        assertThat(recreatedGenerator).isInstanceOf(StandardSessionIdGenerator.class);
        assertThat(recreatedGenerator).isNotSameAs(configuredGenerator);
        assertThat(recreatedGenerator.generateSessionId()).hasSize(32);
        assertThat(manager.getSessionIdGenerator()).isSameAs(recreatedGenerator);
    }

    private static final class RestartableManager extends StandardManager {
        private void releaseGeneratorInstance() {
            sessionIdGenerator = null;
        }
    }
}
