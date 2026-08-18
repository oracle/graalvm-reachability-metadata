/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.header.LabelHeader;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class LabelHeaderTest {
    @Test
    void createsDeltaVLabelHeader() {
        LabelHeader header = new LabelHeader("release-candidate");

        assertThat(header.getLabel()).isEqualTo("release-candidate");
        assertThat(header.getHeaderName()).isEqualTo(DeltaVConstants.HEADER_LABEL);
        assertThat(header.getHeaderValue()).isEqualTo("release-candidate");
    }

    @Test
    void rejectsNullLabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LabelHeader(null))
                .withMessage("null is not a valid label.");
    }
}
