/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

public class ArrayTypeConverterTest {
    @Test
    void convertsCollectionsAndArraysThroughCamelTypeConverter() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.start();
            TypeConverter typeConverter = context.getTypeConverter();

            List<String> names = Arrays.asList("alpha", "beta");
            String[] convertedCollection = typeConverter.mandatoryConvertTo(String[].class, names);

            Object[] numbers = new Object[] {Integer.valueOf(1), Long.valueOf(2)};
            Number[] convertedArray = typeConverter.mandatoryConvertTo(Number[].class, numbers);

            assertThat(convertedCollection).containsExactly("alpha", "beta");
            assertThat(convertedArray).containsExactly(Integer.valueOf(1), Long.valueOf(2));
        } finally {
            context.stop();
        }
    }
}
