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
    void generatesSessionIdWithConfiguredRandomImplementationAndRoute() {
        StandardSessionIdGenerator generator = new StandardSessionIdGenerator();
        generator.setSecureRandomClass(PredictableSecureRandom.class.getName());
        generator.setSessionIdLength(8);
        generator.setJvmRoute("node-a");

        String sessionId = generator.generateSessionId();

        assertThat(sessionId).hasSize(23).endsWith(".node-a");
    }

    public static final class PredictableSecureRandom extends SecureRandom {
        public PredictableSecureRandom() {
        }

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (i + 1);
            }
        }
    }
}
