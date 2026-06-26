/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    public void reflectionHashCodeReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord first = new RankedRecord("id-1", "alpha");
        RankedRecord second = new RankedRecord("id-2", "alpha");

        int firstHash = HashCodeBuilder.reflectionHashCode(first, false);
        int secondHash = HashCodeBuilder.reflectionHashCode(second, false);

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    public void reflectionHashCodeCanIncludeTransientFieldsWhenRequested() {
        SessionRecord first = new SessionRecord("alpha", "session-1");
        SessionRecord second = new SessionRecord("alpha", "session-2");

        int defaultFirstHash = HashCodeBuilder.reflectionHashCode(first, false);
        int defaultSecondHash = HashCodeBuilder.reflectionHashCode(second, false);
        int transientFirstHash = HashCodeBuilder.reflectionHashCode(first, true);
        int transientSecondHash = HashCodeBuilder.reflectionHashCode(second, true);

        assertThat(defaultFirstHash).isEqualTo(defaultSecondHash);
        assertThat(transientFirstHash).isNotEqualTo(transientSecondHash);
    }

    private static class IdentifiedRecord {
        private final String identifier;

        private IdentifiedRecord(String identifier) {
            this.identifier = identifier;
        }
    }

    private static final class RankedRecord extends IdentifiedRecord {
        private final String label;

        private RankedRecord(String identifier, String label) {
            super(identifier);
            this.label = label;
        }
    }

    private static final class SessionRecord {
        private final String label;
        private transient String sessionToken;

        private SessionRecord(String label, String sessionToken) {
            this.label = label;
            this.sessionToken = sessionToken;
        }
    }
}
