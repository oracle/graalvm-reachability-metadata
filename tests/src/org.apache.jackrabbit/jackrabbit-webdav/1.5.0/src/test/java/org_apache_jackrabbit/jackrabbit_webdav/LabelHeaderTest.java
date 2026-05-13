/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.webdav.header.LabelHeader;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.junit.jupiter.api.Test;

public class LabelHeaderTest {
    @Test
    public void constructorStoresLabelAndCreatesEscapedHeaderValue() {
        String label = "release candidate/1";

        LabelHeader header = new LabelHeader(label);

        assertThat(header.getLabel()).isEqualTo(label);
        assertThat(header.getHeaderName()).isEqualTo(DeltaVConstants.HEADER_LABEL);
        assertThat(header.getHeaderValue()).isEqualTo(Text.escape(label));
    }

    @Test
    public void constructorRejectsNullLabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LabelHeader(null))
                .withMessage("null is not a valid label.");
    }
}
