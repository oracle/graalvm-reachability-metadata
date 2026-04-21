/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    public void reflectionCompareReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord lhs = new RankedRecord(1, "alpha", "cache");
        RankedRecord rhs = new RankedRecord(2, "alpha", "cache");

        int comparison = CompareToBuilder.reflectionCompare(lhs, rhs, false, IdentifiedRecord.class);

        assertThat(comparison).isLessThan(0);
    }

    @Test
    public void reflectionCompareCanIncludeTransientFieldsWhenRequested() {
        SessionRecord lhs = new SessionRecord("alpha", "session-1");
        SessionRecord rhs = new SessionRecord("alpha", "session-2");

        int defaultComparison = CompareToBuilder.reflectionCompare(lhs, rhs);
        int transientComparison = CompareToBuilder.reflectionCompare(lhs, rhs, true);

        assertThat(defaultComparison).isZero();
        assertThat(transientComparison).isLessThan(0);
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
        private transient String cachedDisplayName;

        private RankedRecord(int rank, String label, String cachedDisplayName) {
            super(rank);
            this.label = label;
            this.cachedDisplayName = cachedDisplayName;
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
