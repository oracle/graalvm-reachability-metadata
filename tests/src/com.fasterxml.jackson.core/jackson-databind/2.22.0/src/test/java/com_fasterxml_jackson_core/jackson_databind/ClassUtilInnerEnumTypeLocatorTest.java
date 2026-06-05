/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.EnumMap;
import java.util.EnumSet;

import com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilInnerEnumTypeLocatorTest {

    @Test
    void locatesEnumTypeFromEmptyEnumSetAndEnumMap() {
        EnumSet<Drink> drinks = EnumSet.noneOf(Drink.class);
        EnumMap<Drink, String> prices = new EnumMap<>(Drink.class);

        assertThat(ClassUtil.findEnumType(drinks)).isSameAs(Drink.class);
        assertThat(ClassUtil.findEnumType(prices)).isSameAs(Drink.class);
    }

    private enum Drink {
        COFFEE,
        TEA
    }
}
