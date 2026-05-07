/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.MailingList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MailingListTest {
    @Test
    void rejectsNullOtherArchiveWithTypedErrorMessage() {
        MailingList mailingList = new MailingList();

        ClassCastException exception = assertThrows(ClassCastException.class,
                () -> mailingList.addOtherArchive(null));

        assertThat(exception).hasMessageContaining("java.lang.String");
        assertThat(mailingList.getOtherArchives()).isEmpty();
    }
}
