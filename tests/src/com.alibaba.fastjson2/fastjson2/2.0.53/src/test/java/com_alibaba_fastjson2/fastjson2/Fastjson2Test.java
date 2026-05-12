/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_fastjson2.fastjson2;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.PropertyNamingStrategy;
import com.alibaba.fastjson2.util.Fnv;
import org.junit.jupiter.api.Test;

public class Fastjson2Test {
    @Test
    void convertsSnakeCaseNamesToCamelCase() {
        assertThat(PropertyNamingStrategy.snakeToCamel("customer_id")).isEqualTo("customerId");
        assertThat(PropertyNamingStrategy.snakeToCamel("order_line_item")).isEqualTo("orderLineItem");
        assertThat(PropertyNamingStrategy.snakeToCamel("alreadyCamel")).isEqualTo("alreadyCamel");
        assertThat(PropertyNamingStrategy.snakeToCamel(null)).isNull();
    }

    @Test
    void resolvesCommonNamingStrategyAliases() {
        PropertyNamingStrategy upper = PropertyNamingStrategy.of("Upper");
        PropertyNamingStrategy lower = PropertyNamingStrategy.of("lower");
        PropertyNamingStrategy camel = PropertyNamingStrategy.of("Camel");

        assertThat(upper == PropertyNamingStrategy.UpperCase).isTrue();
        assertThat(lower == PropertyNamingStrategy.LowerCase).isTrue();
        assertThat(camel == PropertyNamingStrategy.CamelCase).isTrue();
        assertThat(PropertyNamingStrategy.of("")).isNull();
        assertThat(PropertyNamingStrategy.of("missing-strategy")).isNull();
    }

    @Test
    void resolvesNamingStrategiesByEnumName() {
        PropertyNamingStrategy snakeCase = PropertyNamingStrategy.of("SnakeCase");
        PropertyNamingStrategy kebabCase = PropertyNamingStrategy.of("KebabCase");
        PropertyNamingStrategy lowerCaseWithDots = PropertyNamingStrategy.of("LowerCaseWithDots");

        assertThat(snakeCase == PropertyNamingStrategy.SnakeCase).isTrue();
        assertThat(kebabCase == PropertyNamingStrategy.KebabCase).isTrue();
        assertThat(lowerCaseWithDots == PropertyNamingStrategy.LowerCaseWithDots).isTrue();
    }

    @Test
    void computesStableFieldNameHashes() {
        long idHash = Fnv.hashCode64("id");
        long upperCaseIdHash = Fnv.hashCode64("ID");
        long lowerCaseIdHash = Fnv.hashCode64LCase("ID");
        long snakeCaseHash = Fnv.hashCode64LCase("customer_id");
        long kebabCaseHash = Fnv.hashCode64LCase("Customer-ID");
        long spacedHash = Fnv.hashCode64LCase("customer ID");

        assertThat(idHash).isEqualTo(lowerCaseIdHash);
        assertThat(idHash).isNotEqualTo(upperCaseIdHash);
        assertThat(snakeCaseHash).isEqualTo(kebabCaseHash);
        assertThat(snakeCaseHash).isEqualTo(spacedHash);
        assertThat(Fnv.hashCode64("customer", "id")).isNotEqualTo(Fnv.hashCode64("id", "customer"));
    }
}
