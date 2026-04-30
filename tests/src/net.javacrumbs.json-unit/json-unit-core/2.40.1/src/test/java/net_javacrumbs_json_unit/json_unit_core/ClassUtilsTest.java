/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_javacrumbs_json_unit.json_unit_core;

import net.javacrumbs.jsonunit.core.internal.Diff;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void loadingDiffKeepsValueQuotingAvailableWhenOptionalLoggingIsDetected() {
        Object quotedString = Diff.quoteTextValue("json-unit");
        Object unchangedNumber = Diff.quoteTextValue(42);

        assertThat(quotedString).isEqualTo("\"json-unit\"");
        assertThat(unchangedNumber).isEqualTo(42);
    }
}
