/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.tesseract;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.tesseract.StringVector;
import org.junit.jupiter.api.Test;

public class StringVectorTest {
    @Test
    void mutatesOrderedNativeStringValues() {
        try (StringVector values = new StringVector("eng", "osd")) {
            assertThat(values.empty()).isFalse();
            assertThat(values.size()).isEqualTo(2L);
            assertThat(values.front().getString()).isEqualTo("eng");
            assertThat(values.back().getString()).isEqualTo("osd");

            values.put(1, "equ");
            values.push_back("script/Latin");

            assertThat(values.size()).isEqualTo(3L);
            assertThat(values.get(0).getString()).isEqualTo("eng");
            assertThat(values.get(1).getString()).isEqualTo("equ");
            assertThat(values.get(2).getString()).isEqualTo("script/Latin");

            values.resize(2);
            assertThat(values.back().getString()).isEqualTo("equ");

            values.clear();
            assertThat(values.empty()).isTrue();
            assertThat(values.size()).isZero();
        }
    }
}
