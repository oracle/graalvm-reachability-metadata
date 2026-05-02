/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.jupiter.api.Test;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FrameworkFieldTest {
    @Test
    public void readsFieldValueFromFixtureInstance() throws IllegalAccessException {
        FieldFixture fixture = new FieldFixture("covered value");
        TestClass testClass = new TestClass(FieldFixture.class);

        List<FrameworkField> fields = testClass.getAnnotatedFields(CoveredField.class);

        assertEquals(1, fields.size());
        assertEquals("covered value", fields.get(0).get(fixture));
    }

    @Retention(RUNTIME)
    @Target(FIELD)
    public @interface CoveredField {
    }

    public static class FieldFixture {
        @CoveredField
        public final String value;

        public FieldFixture(String value) {
            this.value = value;
        }
    }
}
