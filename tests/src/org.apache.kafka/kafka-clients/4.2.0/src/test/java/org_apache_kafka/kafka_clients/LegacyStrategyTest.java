/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import javax.security.auth.Subject;

import org.apache.kafka.common.internals.SecurityManagerCompatibility;
import org.junit.jupiter.api.Test;

public class LegacyStrategyTest {

    @Test
    void callAsMakesTheProvidedSubjectCurrentOnlyWithinTheAction() {
        SecurityManagerCompatibility compatibility = SecurityManagerCompatibility.get();
        Subject previousSubject = compatibility.current();
        Subject subject = new Subject();

        Subject observedSubject = compatibility.callAs(subject, compatibility::current);

        assertThat(observedSubject).isSameAs(subject);
        assertThat(compatibility.current()).isEqualTo(previousSubject);
    }

    @Test
    void doPrivilegedReturnsTheActionResult() {
        String result = SecurityManagerCompatibility.get().doPrivileged(() -> "kafka-clients");

        assertThat(result).isEqualTo("kafka-clients");
    }
}
