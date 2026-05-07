/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import org.apache.camel.support.resume.Resumables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResumablesTest {
    @Test
    void resumeEachReturnsTypedArrayContainingOnlyResumableEntries() {
        String[] entries = {"already-processed", "pending-a", "pending-b" };

        String[] resumableEntries = Resumables.resumeEach(entries, entry -> entry.startsWith("pending"));

        assertThat(resumableEntries)
                .isInstanceOf(String[].class)
                .containsExactly("pending-a", "pending-b");
    }

    @Test
    void resumeEachReturnsOriginalArrayWhenAllEntriesAreResumable() {
        Integer[] entries = {1, 2, 3 };

        Integer[] resumableEntries = Resumables.resumeEach(entries, entry -> entry > 0);

        assertThat(resumableEntries).isSameAs(entries);
    }
}
