/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.jsonpath.DocumentContext;
import org.springframework.modulith.runtime.jsonpath.JsonPath;

public class PathFunctionFactoryTest {
    @Test
    void computesNumericAggregates() {
        DocumentContext document = JsonPath.parse("""
                {"dependencies":[2,4,6]}
                """);

        assertThat(document.<Double>read("$.dependencies.sum()")).isEqualTo(12.0);
        assertThat(document.<Double>read("$.dependencies.avg()")).isEqualTo(4.0);
        assertThat(document.<Double>read("$.dependencies.min()")).isEqualTo(2.0);
        assertThat(document.<Double>read("$.dependencies.max()")).isEqualTo(6.0);
        assertThat(document.<Double>read("$.dependencies.stddev()"))
                .isCloseTo(Math.sqrt(8.0 / 3.0), within(0.000001));
    }

    @Test
    void countsAndSelectsArrayElements() {
        DocumentContext document = JsonPath.parse("""
                {"modules":["inventory","catalog","billing"]}
                """);

        assertThat(document.<Integer>read("$.modules.length()")).isEqualTo(3);
        assertThat(document.<Integer>read("$.modules.size()")).isEqualTo(3);
        assertThat(document.<String>read("$.modules.first()")).isEqualTo("inventory");
        assertThat(document.<String>read("$.modules.last()")).isEqualTo("billing");
        assertThat(document.<String>read("$.modules.index(1)")).isEqualTo("catalog");
    }

    @Test
    void enumeratesObjectKeys() {
        Collection<String> names = JsonPath.read("""
                {"modules":{"inventory":{},"billing":{}}}
                """, "$.modules.keys()");

        assertThat(names).containsExactlyInAnyOrder("inventory", "billing");
    }

    @Test
    void concatenatesTextValues() {
        String result = JsonPath.read("""
                {"labels":["inventory","-","api"]}
                """, "$.labels.concat()");

        assertThat(result).isEqualTo("inventory-api");
    }

    @Test
    void appendsArrayValues() {
        List<String> names = JsonPath.read("""
                {"modules":["inventory"]}
                """, "$.modules.append(\"billing\")");

        assertThat(names).containsExactly("inventory", "billing");
    }
}
