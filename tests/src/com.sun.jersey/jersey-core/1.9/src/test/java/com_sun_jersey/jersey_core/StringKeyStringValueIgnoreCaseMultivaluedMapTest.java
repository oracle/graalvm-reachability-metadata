/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.util.StringKeyStringValueIgnoreCaseMultivaluedMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringKeyStringValueIgnoreCaseMultivaluedMapTest {
    @Test
    public void convertsAllStringValuesUsingStringConstructor() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.addObject("Numbers", 10);
        values.addObject("numbers", 20);

        final List<BigInteger> convertedValues = values.get("NUMBERS", BigInteger.class);

        assertThat(convertedValues).containsExactly(new BigInteger("10"), new BigInteger("20"));
    }

    @Test
    public void convertsFirstStringValueUsingExplicitType() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.addObject("Location", "https://example.test/resource");
        values.addObject("location", "https://example.test/ignored");

        final URI firstValue = values.getFirst("LOCATION", URI.class);

        assertThat(firstValue).isEqualTo(URI.create("https://example.test/resource"));
    }

    @Test
    public void convertsFirstStringValueUsingDefaultValueType() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.putSingleObject("Amount", "42.5");

        final BigDecimal firstValue = values.getFirst("amount", BigDecimal.ZERO);

        assertThat(firstValue).isEqualByComparingTo("42.5");
    }
}
