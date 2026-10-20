/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.codec.language.bm.BeiderMorseEncoder;
import org.apache.kylin.jdbc.shaded.org.apache.commons.codec.language.bm.NameType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LangTest {
    @Test
    void loadsLanguageRulesForAshkenaziNames() throws Exception {
        BeiderMorseEncoder encoder = new BeiderMorseEncoder();
        encoder.setNameType(NameType.ASHKENAZI);

        assertThat(encoder.encode("Levine")).isNotBlank().isNotEqualTo("Levine");
    }
}
