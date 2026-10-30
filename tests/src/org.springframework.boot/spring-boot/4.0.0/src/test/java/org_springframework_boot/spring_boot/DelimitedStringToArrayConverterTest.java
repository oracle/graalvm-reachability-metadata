/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.convert.Delimiter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelimitedStringToArrayConverterTest {
    @Test
    void splitsAndConvertsCommaSeparatedElements() {
        ApplicationConversionService service = new ApplicationConversionService();

        assertThat(service.convert(" 7, 11, 19 ", Integer[].class)).containsExactly(7, 11, 19);
        assertThat(service.convert("", String[].class)).isEmpty();
    }

    @Test
    void honorsTheDelimiterOnAConfigurationProperty() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("routes.names", "east; west")));

        Routes routes = binder.bind("routes", Routes.class).get();

        assertThat(routes.names()).containsExactly("east", "west");
    }

    public record Routes(@Delimiter(";") String[] names) {
    }
}
