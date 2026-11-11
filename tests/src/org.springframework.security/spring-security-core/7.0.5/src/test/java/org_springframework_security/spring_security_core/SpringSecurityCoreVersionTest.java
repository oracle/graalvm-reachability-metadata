/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.SpringSecurityCoreVersion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringSecurityCoreVersionTest {
    @Test
    void reportsTheArtifactVersionAndChecksTheFrameworkVersionResource() {
        Logger logger = Logger.getLogger(SpringSecurityCoreVersion.class.getName());
        Level previous = logger.getLevel();
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        StreamHandler capture = new StreamHandler(messages, new SimpleFormatter());
        capture.setLevel(Level.INFO);
        logger.setLevel(Level.INFO);
        logger.addHandler(capture);
        try {
            String version = SpringSecurityCoreVersion.getVersion();
            capture.flush();

            assertNotNull(version);
            assertFalse(version.isBlank());
            assertTrue(messages.toString(StandardCharsets.UTF_8).contains("You are running with Spring Security Core " + version));
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(previous);
            capture.close();
        }
    }
}
