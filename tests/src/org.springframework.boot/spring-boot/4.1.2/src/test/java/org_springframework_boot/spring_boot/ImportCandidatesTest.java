/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ImportCandidatesTest {
    @Test
    void readsOrderedImportCandidatesAndIgnoresComments() {
        ImportCandidates imports = ImportCandidates.load(ImportMarker.class, getClass().getClassLoader());

        assertThat(imports.getCandidates()).containsExactly("example.config.Orders", "example.config.Inventory");
        assertThat(imports).containsExactly("example.config.Orders", "example.config.Inventory");
        assertThatThrownBy(() -> imports.getCandidates().add("another.Configuration"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
