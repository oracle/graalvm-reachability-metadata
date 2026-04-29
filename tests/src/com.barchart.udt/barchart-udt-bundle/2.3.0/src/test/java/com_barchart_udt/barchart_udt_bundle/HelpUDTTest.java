/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_barchart_udt.barchart_udt_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.barchart.udt.OptionUDT;
import com.barchart.udt.util.HelpUDT;
import org.junit.jupiter.api.Test;

public class HelpUDTTest {
    @Test
    void constantFieldNameFindsMatchingPublicStaticConstant() {
        final String fieldName = HelpUDT.constantFieldName(OptionUDT.class, OptionUDT.UDT_MSS);

        assertThat(fieldName).isEqualTo("UDT_MSS");
    }

    @Test
    void optionNameUsesConstantFieldLookup() {
        final String fieldName = OptionUDT.Maximum_Transfer_Unit.name();

        assertThat(fieldName).isEqualTo("Maximum_Transfer_Unit");
    }
}
