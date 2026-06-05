/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonNamingStrategyTest {

    static final ObjectMapper mapper = new ObjectMapper();

    // SnakeCaseStrategy

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BeanSnake(String coffeeType) {}

    @Test
    void serializeObjectWithSnakeCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanSnake("espresso"));
        assertThat(result).isEqualTo("{\"coffee_type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithSnakeCaseStrategy() throws JsonProcessingException {
        BeanSnake bean = mapper.readValue("{\"coffee_type\":\"espresso\"}", BeanSnake.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // UpperSnakeCaseStrategy

    @JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy.class)
    record BeanUpperSnake(String coffeeType) {}

    @Test
    void serializeObjectWithUpperSnakeCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanUpperSnake("espresso"));
        assertThat(result).isEqualTo("{\"COFFEE_TYPE\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithUpperSnakeCaseStrategy() throws JsonProcessingException {
        BeanUpperSnake bean = mapper.readValue("{\"COFFEE_TYPE\":\"espresso\"}", BeanUpperSnake.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerCamelCaseStrategy

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    record BeanLowerCamel(String coffeeType) {}

    @Test
    void serializeObjectWithLowerCamelCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanLowerCamel("espresso"));
        assertThat(result).isEqualTo("{\"coffeeType\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerCamelCaseStrategy() throws JsonProcessingException {
        BeanLowerCamel bean = mapper.readValue("{\"coffeeType\":\"espresso\"}", BeanLowerCamel.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // UpperCamelCaseStrategy

    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    record BeanUpperCamel(String coffeeType) {}

    @Test
    void serializeObjectWithUpperCamelCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanUpperCamel("espresso"));
        assertThat(result).isEqualTo("{\"CoffeeType\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithUpperCamelCaseStrategy() throws JsonProcessingException {
        BeanUpperCamel bean = mapper.readValue("{\"CoffeeType\":\"espresso\"}", BeanUpperCamel.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerCaseStrategy

    @JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
    record BeanLower(String coffeeType) {}

    @Test
    void serializeObjectWithLowerCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanLower("espresso"));
        assertThat(result).isEqualTo("{\"coffeetype\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerCaseStrategy() throws JsonProcessingException {
        BeanLower bean = mapper.readValue("{\"coffeetype\":\"espresso\"}", BeanLower.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // KebabCaseStrategy

    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    record BeanKebab(String coffeeType) {}

    @Test
    void serializeObjectWithKebabCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanKebab("espresso"));
        assertThat(result).isEqualTo("{\"coffee-type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithKebabCaseStrategy() throws JsonProcessingException {
        BeanKebab bean = mapper.readValue("{\"coffee-type\":\"espresso\"}", BeanKebab.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

    // LowerDotCaseStrategy

    @JsonNaming(PropertyNamingStrategies.LowerDotCaseStrategy.class)
    record BeanLowerDot(String coffeeType) {}

    @Test
    void serializeObjectWithLowerDotCaseStrategy() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new BeanLowerDot("espresso"));
        assertThat(result).isEqualTo("{\"coffee.type\":\"espresso\"}");
    }

    @Test
    void deserializeObjectWithLowerDotCaseStrategy() throws JsonProcessingException {
        BeanLowerDot bean = mapper.readValue("{\"coffee.type\":\"espresso\"}", BeanLowerDot.class);
        assertThat(bean.coffeeType()).isEqualTo("espresso");
    }

}
