/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.validation.DataBinder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DataBinderTest {
    @Test
    void constructsArrayArgumentFromIndexedInput() {
        Map<String, String> values = Map.of("labels[0]", "north", "labels[1]", "south");
        DataBinder binder = new DataBinder(null);
        binder.setTargetType(ResolvableType.forClass(Shipment.class));
        binder.construct(new DataBinder.ValueResolver() {
            @Override
            public Object resolveValue(String name, Class<?> type) {
                return values.get(name);
            }

            @Override
            public Set<String> getNames() {
                return values.keySet();
            }
        });
        assertFalse(binder.getBindingResult().hasErrors());
        Shipment shipment = assertInstanceOf(Shipment.class, binder.getTarget());
        assertArrayEquals(new String[]{"north", "south"}, shipment.labels());
    }

    public record Shipment(String[] labels) {
    }
}
