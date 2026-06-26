/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_archiver;

import org.codehaus.plexus.archiver.zip.AsiExtraField;
import org.codehaus.plexus.archiver.zip.ExtraFieldUtils;
import org.codehaus.plexus.archiver.zip.ZipExtraField;
import org.codehaus.plexus.archiver.zip.ZipShort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldUtilsTest {

    @Test
    void registerMakesExtraFieldAvailableForCreation() throws Exception {
        AsiExtraField registeredField = new AsiExtraField();
        ZipShort headerId = registeredField.getHeaderId();

        ExtraFieldUtils.register(AsiExtraField.class);
        ZipExtraField recreatedField = ExtraFieldUtils.createExtraField(headerId);

        assertThat(recreatedField).isInstanceOf(AsiExtraField.class);
        assertThat(recreatedField).isNotSameAs(registeredField);
        assertThat(recreatedField.getHeaderId()).isEqualTo(headerId);
    }
}
