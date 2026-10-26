/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.codec.language.bm.BeiderMorseEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleTest {
    @Test
    void appliesBundledPhoneticRulesToAName() throws Exception {
        String encoded = new BeiderMorseEncoder().encode("Schneider");

        assertThat(encoded).isNotBlank().isNotEqualTo("Schneider");
    }
}
