/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

public class ArrayBuildersTest {
    @Test
    public void insertInListPrependsElementInNewArrayWithSameComponentType() {
        String[] values = new String[] {"first", "second"};

        String[] result = ArrayBuilders.insertInList(values, "new");

        assertThat(result).isNotSameAs(values);
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result).containsExactly("new", "first", "second");
        assertThat(values).containsExactly("first", "second");
    }

    @Test
    public void insertInListNoDupAllocatesSameComponentArrayForDuplicateNotAtHead() {
        String first = "first";
        String duplicate = "duplicate";
        String[] values = new String[] {first, duplicate, "last"};

        String[] result = ArrayBuilders.insertInListNoDup(values, duplicate);

        assertThat(result).isNotSameAs(values);
        assertThat(result).hasSameSizeAs(values);
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result[1]).isSameAs(first);
    }

    @Test
    public void insertInListNoDupPrependsMissingElementInNewArrayWithSameComponentType() {
        String[] values = new String[] {"first", "second"};

        String[] result = ArrayBuilders.insertInListNoDup(values, "new");

        assertThat(result).isNotSameAs(values);
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result).containsExactly("new", "first", "second");
        assertThat(values).containsExactly("first", "second");
    }
}
