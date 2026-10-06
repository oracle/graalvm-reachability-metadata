/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.security.SecureRandom;

import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdGeneratorBaseTest {

    @Test
    void generatesSessionIdWithConfiguredRandomClassAndRoute() {
        StandardSessionIdGenerator generator = new StandardSessionIdGenerator();
        generator.setSecureRandomClass(SecureRandom.class.getName());
        generator.setSessionIdLength(16);
        generator.setJvmRoute("node1");

        String sessionId = generator.generateSessionId();

        assertThat(sessionId).hasSize(38);
        assertThat(sessionId).endsWith(".node1");
    }
}
