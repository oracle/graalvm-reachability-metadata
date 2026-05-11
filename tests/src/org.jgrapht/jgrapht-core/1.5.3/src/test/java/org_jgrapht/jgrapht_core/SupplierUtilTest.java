/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class SupplierUtilTest {
    @Test
    void createsSupplierFromCustomTypeDefaultConstructor() {
        Supplier<SuppliedValue> supplier = SupplierUtil.createSupplier(SuppliedValue.class);

        SuppliedValue first = supplier.get();
        SuppliedValue second = supplier.get();

        assertThat(first).isNotSameAs(second);
        assertThat(first.value()).isEqualTo("constructed");
        assertThat(second.value()).isEqualTo("constructed");
    }

    public static final class SuppliedValue {
        private final String value;

        public SuppliedValue() {
            this.value = "constructed";
        }

        String value() {
            return value;
        }
    }
}
