/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.internal.annotations.Converter;

@SuppressWarnings("deprecation")
public class ConverterTest {
    @Test
    void convertsClassNameListIntoClassArray() {
        Class<?>[] defaultClasses = new Class<?>[] {Object.class};

        Class<?>[] classes = Converter.getClassArray(
                String.class.getName() + ", " + ConverterTest.class.getName(),
                defaultClasses);

        assertThat(classes).containsExactly(String.class, ConverterTest.class);
    }

    @Test
    void returnsDefaultClassArrayWhenTagValueIsNull() {
        Class<?>[] defaultClasses = new Class<?>[] {Object.class};

        Class<?>[] classes = Converter.getClassArray(null, defaultClasses);

        assertThat(classes).isSameAs(defaultClasses);
    }
}
