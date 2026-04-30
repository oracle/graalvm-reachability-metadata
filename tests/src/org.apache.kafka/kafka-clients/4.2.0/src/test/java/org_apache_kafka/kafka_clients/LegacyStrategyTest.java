/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.security.Principal;

import javax.security.auth.Subject;

import org.apache.kafka.common.internals.SecurityManagerCompatibility;
import org.junit.jupiter.api.Test;

public class LegacyStrategyTest {
    @Test
    void initializesLegacyCompatibilityStrategy() {
        SecurityManagerCompatibility compatibility = SecurityManagerCompatibility.get();

        String result = compatibility.doPrivileged(() -> "legacy-strategy-initialized");

        assertEquals("legacy-strategy-initialized", result);
    }

    @Test
    void exposesCurrentSubjectInsideCallAs() {
        SecurityManagerCompatibility compatibility = SecurityManagerCompatibility.get();
        Subject subject = new Subject();
        Principal principal = () -> "kafka-test-principal";
        subject.getPrincipals().add(principal);

        Subject currentSubject = compatibility.callAs(subject, compatibility::current);

        assertSame(subject, currentSubject);
    }
}
