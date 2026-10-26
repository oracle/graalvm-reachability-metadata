/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;

import static org.assertj.core.api.Assertions.assertThat;

public class DelimitedStringToArrayConverterTest {

    @Test
    void splitsCommaDelimitedStringIntoTypedArray() {
        ConversionService conversionService = new ApplicationConversionService();

        assertThat(conversionService.convert(" alpha,  beta,gamma ", String[].class))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(conversionService.convert("1, 2, 3", Integer[].class)).containsExactly(1, 2, 3);
    }

    @Test
    void binderConvertsDelimitedPropertyValueToArray() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.profiles", "dev,test,prod");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        String[] profiles = binder.bind("app.profiles", Bindable.of(String[].class)).get();

        assertThat(profiles).containsExactly("dev", "test", "prod");
    }
}
