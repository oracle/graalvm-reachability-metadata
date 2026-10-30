/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** Verifies transitive binding hints for records, bean properties, and conversion factories. */
public class BindingReflectionHintsRegistrarTest {
    @Test
    void registersRecordComponentsAndNestedBeanProperties() {
        RuntimeHints hints = new RuntimeHints();

        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), Shipment.class);

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Shipment.class, "address").test(hints))
                .isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Address.class, "getCity").test(hints))
                .isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Address.class, "setCity").test(hints))
                .isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Address.class, "of").test(hints))
                .isTrue();
        assertThat(hints.reflection().getTypeHint(Shipment.class).getMemberCategories())
                .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }

    public record Shipment(Address address) { }

    public static class Address {
        private String city;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public static Address of(String city) {
            Address address = new Address();
            address.setCity(city);
            return address;
        }
    }
}
