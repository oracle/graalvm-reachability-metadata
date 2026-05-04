/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.os_zip;

import os.shaded_org_apache_tools_zip.AsiExtraField;
import os.shaded_org_apache_tools_zip.ExtraFieldUtils;
import os.shaded_org_apache_tools_zip.ZipExtraField;
import os.shaded_org_apache_tools_zip.ZipShort;
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
