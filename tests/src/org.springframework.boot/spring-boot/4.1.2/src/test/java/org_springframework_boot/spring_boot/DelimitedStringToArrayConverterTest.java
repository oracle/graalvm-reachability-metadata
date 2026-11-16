/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import static org.assertj.core.api.Assertions.assertThat;

public class DelimitedStringToArrayConverterTest {
    @Test
    void convertsDelimitedValuesToTypedArrays() {
        ApplicationConversionService service = new ApplicationConversionService();

        assertThat(service.convert("12, 24,36", Integer[].class)).containsExactly(12, 24, 36);
        assertThat(service.convert(" north, south ", String[].class)).containsExactly("north", "south");
        assertThat(service.convert("", Integer[].class)).isEmpty();
    }
}
