/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

public class ArrayConverterTest {
    @Test
    void mapsCollectionElementsIntoNewDestinationArray() {
        ModelMapper modelMapper = new ModelMapper();
        List<String> sourceItems = Arrays.asList("alpha", "bravo", "charlie");

        String[] destinationItems = modelMapper.map(sourceItems, String[].class);

        assertThat(destinationItems).containsExactly("alpha", "bravo", "charlie");
    }
}
