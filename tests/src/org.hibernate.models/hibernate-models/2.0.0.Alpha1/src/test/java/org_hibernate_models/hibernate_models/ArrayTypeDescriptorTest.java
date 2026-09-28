/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import org.hibernate.models.internal.ArrayTypeDescriptor;
import org.hibernate.models.internal.StringTypeDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeDescriptorTest {
    @Test
    public void unwrapsValuesIntoAnArrayOfTheElementType() {
        final ArrayTypeDescriptor<String> descriptor = new ArrayTypeDescriptor<>(
                StringTypeDescriptor.STRING_TYPE_DESCRIPTOR
        );

        final String[] values = (String[]) descriptor.unwrap(new String[] {"first", "second"});

        assertThat(values).isExactlyInstanceOf(String[].class);
        assertThat(values).containsExactly("first", "second");
    }
}
