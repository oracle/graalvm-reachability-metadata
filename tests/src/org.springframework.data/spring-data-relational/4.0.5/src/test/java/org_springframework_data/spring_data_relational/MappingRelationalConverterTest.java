/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_relational;

import org.junit.jupiter.api.Test;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.relational.core.conversion.MappingRelationalConverter;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class MappingRelationalConverterTest {

    @Test
    void writesEnumArrayAsStringArray() {
        MappingRelationalConverter converter = new MappingRelationalConverter(new RelationalMappingContext());
        OrderStatus[] statuses = {OrderStatus.RECEIVED, OrderStatus.SHIPPED, OrderStatus.RECEIVED};

        Object written = converter.writeValue(statuses, TypeInformation.of(String[].class));

        assertArrayEquals(new String[]{"RECEIVED", "SHIPPED", "RECEIVED"}, (String[]) written);
    }

    @Test
    void writesEnumCollectionAsStringArray() {
        MappingRelationalConverter converter = new MappingRelationalConverter(new RelationalMappingContext());
        List<OrderStatus> statuses = List.of(OrderStatus.SHIPPED, OrderStatus.RECEIVED);

        Object written = converter.writeValue(statuses, TypeInformation.of(String[].class));

        assertArrayEquals(new String[]{"SHIPPED", "RECEIVED"}, (String[]) written);
    }

    public enum OrderStatus {
        RECEIVED,
        SHIPPED
    }
}
