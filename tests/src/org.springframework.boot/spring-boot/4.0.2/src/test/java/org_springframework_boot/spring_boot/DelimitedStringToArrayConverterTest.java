/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import static org.assertj.core.api.Assertions.assertThat;

public class DelimitedStringToArrayConverterTest {

    @Test
    void convertsTrimmedCommaDelimitedValuesIntoTypedArrays() {
        ApplicationConversionService service = new ApplicationConversionService();

        assertThat(service.convert(" 10ms, 2s, 1m ", Duration[].class))
                .containsExactly(Duration.ofMillis(10), Duration.ofSeconds(2), Duration.ofMinutes(1));
        assertThat(service.convert(" 3, 5, 8 ", Integer[].class)).containsExactly(3, 5, 8);
        assertThat(service.convert("", Duration[].class)).isEmpty();
    }
}
