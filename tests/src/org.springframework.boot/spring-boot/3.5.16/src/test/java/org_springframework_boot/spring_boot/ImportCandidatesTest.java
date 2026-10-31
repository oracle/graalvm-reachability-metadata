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

public class ImportCandidatesTest {

    @Test
    void discoversImportNamesAndRemovesCommentsAndBlankLines() {
        ImportCandidates imports = ImportCandidates.load(EnableMail.class, getClass().getClassLoader());

        assertThat(imports.getCandidates()).containsExactly(
                "example.mail.OutboundConfiguration", "example.mail.InboundConfiguration");
        assertThat(imports).containsExactlyElementsOf(imports.getCandidates());
    }

    public @interface EnableMail {
    }
}
