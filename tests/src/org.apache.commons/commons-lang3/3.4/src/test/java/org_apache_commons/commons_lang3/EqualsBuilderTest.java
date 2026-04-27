/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    public void reflectionEqualsReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord lhs = new RankedRecord("id-1", "alpha");
        RankedRecord rhs = new RankedRecord("id-2", "alpha");

        boolean equal = EqualsBuilder.reflectionEquals(lhs, rhs);

        assertThat(equal).isFalse();
    }

    @Test
    public void reflectionEqualsCanIncludeTransientFieldsWhenRequested() {
        SessionRecord lhs = new SessionRecord("alpha", "session-1");
        SessionRecord rhs = new SessionRecord("alpha", "session-2");

        boolean defaultEquality = EqualsBuilder.reflectionEquals(lhs, rhs);
        boolean transientEquality = EqualsBuilder.reflectionEquals(lhs, rhs, true);

        assertThat(defaultEquality).isTrue();
        assertThat(transientEquality).isFalse();
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
