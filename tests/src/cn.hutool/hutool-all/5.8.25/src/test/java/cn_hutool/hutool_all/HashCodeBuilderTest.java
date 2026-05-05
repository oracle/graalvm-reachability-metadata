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
    void reflectionHashCodeReadsPrivateFieldsAcrossClassHierarchy() {
        VersionedRecord lhs = new VersionedRecord("item-1", "alpha", 3);
        VersionedRecord matching = new VersionedRecord("item-1", "alpha", 3);
        VersionedRecord differentRevision = new VersionedRecord("item-1", "alpha", 4);

        int lhsHash = HashCodeBuilder.reflectionHashCode(17, 37, lhs, false,
                IdentifiedRecord.class);
        int matchingHash = HashCodeBuilder.reflectionHashCode(17, 37, matching, false,
                IdentifiedRecord.class);
        int differentRevisionHash = HashCodeBuilder.reflectionHashCode(17, 37, differentRevision, false,
                IdentifiedRecord.class);

        assertThat(lhsHash).isEqualTo(matchingHash);
        assertThat(lhsHash).isNotEqualTo(differentRevisionHash);
    }

    @Test
    void reflectionHashCodeHonorsTransientAndExcludedFields() {
        CachedRecord lhs = new CachedRecord("item-1", "left-cache", "left-note");
        CachedRecord rhs = new CachedRecord("item-1", "right-cache", "right-note");

        int ignoresTransientAndExcludedFields = HashCodeBuilder.reflectionHashCode(lhs, "note");
        int matchingIgnoredHash = HashCodeBuilder.reflectionHashCode(rhs, "note");
        int includesTransientField = HashCodeBuilder.reflectionHashCode(17, 37, lhs, true, null, "note");
        int differentTransientHash = HashCodeBuilder.reflectionHashCode(17, 37, rhs, true, null, "note");

        assertThat(ignoresTransientAndExcludedFields).isEqualTo(matchingIgnoredHash);
        assertThat(includesTransientField).isNotEqualTo(differentTransientHash);
    }

    private static class IdentifiedRecord {
        private final String id;

        IdentifiedRecord(String id) {
            this.id = id;
        }
    }

    private static final class VersionedRecord extends IdentifiedRecord {
        private static final String TYPE = "versioned";

        private final String label;
        private final int revision;

        VersionedRecord(String id, String label, int revision) {
            super(id);
            this.label = label;
            this.revision = revision;
        }
    }

    private static final class CachedRecord {
        private final String id;
        private transient String cachedValue;
        private final String note;

        CachedRecord(String id, String cachedValue, String note) {
            this.id = id;
            this.cachedValue = cachedValue;
            this.note = note;
        }
    }
}
