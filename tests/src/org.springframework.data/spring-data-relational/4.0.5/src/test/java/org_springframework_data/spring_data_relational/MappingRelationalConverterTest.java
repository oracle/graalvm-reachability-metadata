/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_relational;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.relational.core.conversion.MappingRelationalConverter;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingRelationalConverterTest {

    @Test
    void writesEnumArrayAsStringArray() {
        MappingRelationalConverter converter = new MappingRelationalConverter(new RelationalMappingContext());

        Object written = converter.writeValue(new Status[] { Status.ACTIVE, Status.ARCHIVED },
                TypeInformation.of(Status[].class));

        assertThat(written).isInstanceOf(String[].class);
        assertThat((String[]) written).containsExactly("ACTIVE", "ARCHIVED");
    }

    @Test
    void writesCollectionToArrayWhenTargetCollectionTypeDiffersFromMappedList() {
        MappingRelationalConverter converter = new MappingRelationalConverter(new RelationalMappingContext());

        Object written = converter.writeValue(List.of(Status.ACTIVE, Status.ARCHIVED), TypeInformation.of(Set.class));

        assertThat(written).isInstanceOf(String[].class);
        assertThat((String[]) written).containsExactly("ACTIVE", "ARCHIVED");
    }

    private enum Status {
        ACTIVE,
        ARCHIVED
    }
}
