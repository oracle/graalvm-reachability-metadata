/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_model_base;

import io.sundr.model.ValueRef;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueRefTest {
    @Test
    void createsArrayFromFirstValueAndVarargs() {
        ValueRef valueRef = ValueRef.from("alpha", "beta", "gamma");

        assertThat((String[]) valueRef.getValue()).containsExactly("alpha", "beta", "gamma");
        assertThat(valueRef.render()).isEqualTo("{\"alpha\", \"beta\", \"gamma\"}");
    }
}
