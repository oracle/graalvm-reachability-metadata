/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.pulsar.shade.org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.pulsar.shade.org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.pulsar.shade.org.apache.commons.compress.archivers.zip.ZipShort;
import org.junit.jupiter.api.Test;

public class ExtraFieldUtilsTest {
    @Test
    void registersAndRecreatesZipExtraFields() throws Exception {
        AsiExtraField field = new AsiExtraField();
        ZipShort headerId = field.getHeaderId();

        ExtraFieldUtils.register(AsiExtraField.class);
        ZipExtraField recreated = ExtraFieldUtils.createExtraFieldNoDefault(headerId);

        assertThat(recreated).isInstanceOf(AsiExtraField.class).isNotSameAs(field);
        assertThat(recreated.getHeaderId()).isEqualTo(headerId);
    }
}
