/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareToBuilderTest {

    @Test
    void reflectionCompareReadsPrivateFieldsAcrossClassHierarchy() {
        RankedRecord lhs = new RankedRecord(1, "alpha", "cache");
        RankedRecord rhs = new RankedRecord(2, "alpha", "cache");

        int comparison = CompareToBuilder.reflectionCompare(lhs, rhs, false, IdentifiedRecord.class);

        assertThat(comparison).isLessThan(0);
    }

    @Test
    void reflectionCompareHonorsTransientAndExcludedFields() {
        SessionRecord lhs = new SessionRecord("alpha", "session-1", 10);
        SessionRecord rhs = new SessionRecord("alpha", "session-2", 20);

        int excludedComparison = CompareToBuilder.reflectionCompare(lhs, rhs, "priority");
        int transientComparison = CompareToBuilder.reflectionCompare(lhs, rhs, true, null, "priority");

        assertThat(excludedComparison).isZero();
        assertThat(transientComparison).isLessThan(0);
    }

    private static class IdentifiedRecord {
        private final int rank;

        IdentifiedRecord(int rank) {
            this.rank = rank;
        }
    }

    private static final class RankedRecord extends IdentifiedRecord {
        private static final String TYPE = "ranked";

        private final String label;
        private transient String cachedDisplayName;

        RankedRecord(int rank, String label, String cachedDisplayName) {
            super(rank);
            this.label = label;
            this.cachedDisplayName = cachedDisplayName;
        }
    }

    private static final class SessionRecord {
        private final String label;
        private transient String sessionToken;
        private final int priority;

        SessionRecord(String label, String sessionToken, int priority) {
            this.label = label;
            this.sessionToken = sessionToken;
            this.priority = priority;
        }
    }
}
