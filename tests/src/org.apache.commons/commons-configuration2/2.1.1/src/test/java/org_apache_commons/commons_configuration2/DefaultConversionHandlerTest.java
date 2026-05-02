/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.apache.commons.configuration2.convert.DefaultConversionHandler;
import org.junit.jupiter.api.Test;

public class DefaultConversionHandlerTest {
    private final DefaultConversionHandler conversionHandler = new DefaultConversionHandler();

    @Test
    void convertsEmptyElementToEmptyArray() {
        final Object array = conversionHandler.toArray("", String.class, null);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).isEmpty();
    }

    @Test
    void convertsCollectionToObjectArray() {
        final Object array = conversionHandler.toArray(
                Arrays.asList("7", "11"), Integer.class, null);

        assertThat(array).isInstanceOf(Integer[].class);
        assertThat((Integer[]) array).containsExactly(7, 11);
    }

    @Test
    void convertsWrapperArrayToPrimitiveArray() {
        final Object array = conversionHandler.toArray(new Integer[] {2, 3, 5}, int.class, null);

        assertThat(array).isInstanceOf(int[].class);
        assertThat((int[]) array).containsExactly(2, 3, 5);
    }

    @Test
    void convertsCollectionToPrimitiveArray() {
        final Object array = conversionHandler.toArray(Arrays.asList("13", "17"), int.class, null);

        assertThat(array).isInstanceOf(int[].class);
        assertThat((int[]) array).containsExactly(13, 17);
    }
}
