/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualsBuilderTest {

    @Test
    void reflectionEqualsReadsPrivateFieldsAcrossClassHierarchy() {
        VersionedRecord lhs = new VersionedRecord("item-1", "alpha", 3);
        VersionedRecord matching = new VersionedRecord("item-1", "alpha", 3);
        VersionedRecord differentRevision = new VersionedRecord("item-1", "alpha", 4);

        assertThat(EqualsBuilder.reflectionEquals(lhs, matching)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(lhs, differentRevision)).isFalse();
    }

    @Test
    void reflectionEqualsHonorsTransientAndExcludedFields() {
        CachedRecord lhs = new CachedRecord("item-1", "left-cache", "left-note");
        CachedRecord rhs = new CachedRecord("item-1", "right-cache", "right-note");

        boolean ignoresTransientAndExcludedFields = EqualsBuilder.reflectionEquals(lhs, rhs, false, null, "note");
        boolean includesTransientField = EqualsBuilder.reflectionEquals(lhs, rhs, true, null, "note");

        assertThat(ignoresTransientAndExcludedFields).isTrue();
        assertThat(includesTransientField).isFalse();
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
