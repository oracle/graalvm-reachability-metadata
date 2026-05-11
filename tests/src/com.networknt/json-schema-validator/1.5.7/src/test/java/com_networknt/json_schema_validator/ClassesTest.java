/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.utils.Classes;
import org.junit.jupiter.api.Test;

public class ClassesTest {
    @Test
    void reportsPresentClassUsingProvidedClassLoader() {
        ClassLoader classLoader = ClassesTest.class.getClassLoader();

        boolean present = Classes.isPresent(ClassesTest.class.getName(), classLoader);

        assertThat(present).isTrue();
    }

    @Test
    void reportsMissingClassUsingProvidedClassLoader() {
        ClassLoader classLoader = ClassesTest.class.getClassLoader();

        boolean present = Classes.isPresent("com_networknt.json_schema_validator.DoesNotExist", classLoader);

        assertThat(present).isFalse();
    }
}
