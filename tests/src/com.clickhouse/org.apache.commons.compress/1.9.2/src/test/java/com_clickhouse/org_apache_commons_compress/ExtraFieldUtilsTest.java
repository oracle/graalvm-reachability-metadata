/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_apache_commons_compress;

import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldUtilsTest {

    @Test
    void registerMakesExtraFieldAvailableForCreationWithoutDefaultFallback() throws Exception {
        AsiExtraField registeredField = new AsiExtraField();
        ZipShort headerId = registeredField.getHeaderId();

        ExtraFieldUtils.register(AsiExtraField.class);
        ZipExtraField recreatedField = ExtraFieldUtils.createExtraFieldNoDefault(headerId);

        assertThat(recreatedField).isInstanceOf(AsiExtraField.class);
        assertThat(recreatedField).isNotSameAs(registeredField);
        assertThat(recreatedField.getHeaderId()).isEqualTo(headerId);
    }
}
