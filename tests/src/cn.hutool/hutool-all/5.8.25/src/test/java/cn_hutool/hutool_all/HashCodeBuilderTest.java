/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeBuilderTest {
    @Test
    public void reflectionHashCodeReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord lhs = new RankedRecord(1, "alpha", "cache");
        RankedRecord rhs = new RankedRecord(2, "alpha", "cache");

        int leftHashCode = HashCodeBuilder.reflectionHashCode(17, 37, lhs, false, IdentifiedRecord.class);
        int rightHashCode = HashCodeBuilder.reflectionHashCode(17, 37, rhs, false, IdentifiedRecord.class);

        assertThat(leftHashCode).isNotEqualTo(rightHashCode);
    }

    @Test
    public void reflectionHashCodeCanIncludeTransientFieldsWhenRequested() {
        SessionRecord lhs = new SessionRecord("alpha", "session-1");
        SessionRecord rhs = new SessionRecord("alpha", "session-2");

        int defaultLeftHashCode = HashCodeBuilder.reflectionHashCode(lhs);
        int defaultRightHashCode = HashCodeBuilder.reflectionHashCode(rhs);
        int transientLeftHashCode = HashCodeBuilder.reflectionHashCode(lhs, true);
        int transientRightHashCode = HashCodeBuilder.reflectionHashCode(rhs, true);

        assertThat(defaultLeftHashCode).isEqualTo(defaultRightHashCode);
        assertThat(transientLeftHashCode).isNotEqualTo(transientRightHashCode);
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
