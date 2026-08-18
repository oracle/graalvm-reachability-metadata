/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeBuilderTest {
    @Test
    void reflectionHashCodeReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord record = new RankedRecord(7, "alpha", "session-1");
        RankedRecord samePersistentState = new RankedRecord(7, "alpha", "session-2");
        RankedRecord differentRank = new RankedRecord(8, "alpha", "session-1");
        RankedRecord differentLabel = new RankedRecord(7, "beta", "session-1");

        assertThat(HashCodeBuilder.reflectionHashCode(record))
                .isEqualTo(HashCodeBuilder.reflectionHashCode(samePersistentState));
        assertThat(HashCodeBuilder.reflectionHashCode(record))
                .isNotEqualTo(HashCodeBuilder.reflectionHashCode(differentRank));
        assertThat(HashCodeBuilder.reflectionHashCode(record))
                .isNotEqualTo(HashCodeBuilder.reflectionHashCode(differentLabel));
    }

    @Test
    void reflectionHashCodeCanIncludeTransientFieldsWhenRequested() {
        RankedRecord record = new RankedRecord(7, "alpha", "session-1");
        RankedRecord differentSession = new RankedRecord(7, "alpha", "session-2");

        assertThat(HashCodeBuilder.reflectionHashCode(record))
                .isEqualTo(HashCodeBuilder.reflectionHashCode(differentSession));
        assertThat(HashCodeBuilder.reflectionHashCode(record, true))
                .isNotEqualTo(HashCodeBuilder.reflectionHashCode(differentSession, true));
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
