/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonNamingStrategyTest {

    // SnakeCaseStrategy

    record BeanSnake(String coffeeType) {}

    @Test
    void serializeObjectWithSnakeCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.SNAKE_CASE).writeValueAsString(new BeanSnake("espresso"));
        assertThat(result).isEqualTo("{\"coffee_type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithSnakeCaseStrategy() throws JsonProcessingException {
        BeanSnake bean = mapper(PropertyNamingStrategies.SNAKE_CASE)
                .readValue("{\"coffee_type\":\"espresso\"}", BeanSnake.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // UpperSnakeCaseStrategy

    record BeanUpperSnake(String coffeeType) {}

    @Test
    void serializeObjectWithUpperSnakeCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.UPPER_SNAKE_CASE)
                .writeValueAsString(new BeanUpperSnake("espresso"));
        assertThat(result).isEqualTo("{\"COFFEE_TYPE\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithUpperSnakeCaseStrategy() throws JsonProcessingException {
        BeanUpperSnake bean = mapper(PropertyNamingStrategies.UPPER_SNAKE_CASE)
                .readValue("{\"COFFEE_TYPE\":\"espresso\"}", BeanUpperSnake.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerCamelCaseStrategy

    record BeanLowerCamel(String coffeeType) {}

    @Test
    void serializeObjectWithLowerCamelCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .writeValueAsString(new BeanLowerCamel("espresso"));
        assertThat(result).isEqualTo("{\"coffeeType\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerCamelCaseStrategy() throws JsonProcessingException {
        BeanLowerCamel bean = mapper(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .readValue("{\"coffeeType\":\"espresso\"}", BeanLowerCamel.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // UpperCamelCaseStrategy

    record BeanUpperCamel(String coffeeType) {}

    @Test
    void serializeObjectWithUpperCamelCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.UPPER_CAMEL_CASE)
                .writeValueAsString(new BeanUpperCamel("espresso"));
        assertThat(result).isEqualTo("{\"CoffeeType\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithUpperCamelCaseStrategy() throws JsonProcessingException {
        BeanUpperCamel bean = mapper(PropertyNamingStrategies.UPPER_CAMEL_CASE)
                .readValue("{\"CoffeeType\":\"espresso\"}", BeanUpperCamel.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerCaseStrategy

    record BeanLower(String coffeeType) {}

    @Test
    void serializeObjectWithLowerCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.LOWER_CASE).writeValueAsString(new BeanLower("espresso"));
        assertThat(result).isEqualTo("{\"coffeetype\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerCaseStrategy() throws JsonProcessingException {
        BeanLower bean = mapper(PropertyNamingStrategies.LOWER_CASE)
                .readValue("{\"coffeetype\":\"espresso\"}", BeanLower.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // KebabCaseStrategy

    record BeanKebab(String coffeeType) {}

    @Test
    void serializeObjectWithKebabCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.KEBAB_CASE).writeValueAsString(new BeanKebab("espresso"));
        assertThat(result).isEqualTo("{\"coffee-type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithKebabCaseStrategy() throws JsonProcessingException {
        BeanKebab bean = mapper(PropertyNamingStrategies.KEBAB_CASE)
                .readValue("{\"coffee-type\":\"espresso\"}", BeanKebab.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerDotCaseStrategy

    record BeanLowerDot(String coffeeType) {}

    @Test
    void serializeObjectWithLowerDotCaseStrategy() throws JsonProcessingException {
        String result = mapper(PropertyNamingStrategies.LOWER_DOT_CASE)
                .writeValueAsString(new BeanLowerDot("espresso"));
        assertThat(result).isEqualTo("{\"coffee.type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerDotCaseStrategy() throws JsonProcessingException {
        BeanLowerDot bean = mapper(PropertyNamingStrategies.LOWER_DOT_CASE)
                .readValue("{\"coffee.type\":\"espresso\"}", BeanLowerDot.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    private static ObjectMapper mapper(PropertyNamingStrategy strategy) {
        return new ObjectMapper().setPropertyNamingStrategy(strategy);
    }

}
