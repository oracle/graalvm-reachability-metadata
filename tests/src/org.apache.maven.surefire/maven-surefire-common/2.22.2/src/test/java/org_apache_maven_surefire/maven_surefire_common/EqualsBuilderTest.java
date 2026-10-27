/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualsBuilderTest {
    @Test
    void reflectionEqualsReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord lhs = new RankedRecord(7, "alpha", "session-1");
        RankedRecord samePersistentState = new RankedRecord(7, "alpha", "session-2");
        RankedRecord differentRank = new RankedRecord(8, "alpha", "session-1");
        RankedRecord differentLabel = new RankedRecord(7, "beta", "session-1");

        assertThat(EqualsBuilder.reflectionEquals(lhs, samePersistentState)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(lhs, differentRank)).isFalse();
        assertThat(EqualsBuilder.reflectionEquals(lhs, differentLabel)).isFalse();
    }

    @Test
    void reflectionEqualsCanIncludeTransientFieldsWhenRequested() {
        RankedRecord lhs = new RankedRecord(7, "alpha", "session-1");
        RankedRecord rhs = new RankedRecord(7, "alpha", "session-2");

        assertThat(EqualsBuilder.reflectionEquals(lhs, rhs)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(lhs, rhs, true)).isFalse();
    }

    private static class IdentifiedRecord {
        private final int rank;

        private IdentifiedRecord(int rank) {
            this.rank = rank;
        }
    }

    private static final class RankedRecord extends IdentifiedRecord {
        private static final String TYPE = "ranked";

        private final String label;
        private transient String sessionToken;

        private RankedRecord(int rank, String label, String sessionToken) {
            super(rank);
            this.label = label;
            this.sessionToken = sessionToken;
        }
    }
}
