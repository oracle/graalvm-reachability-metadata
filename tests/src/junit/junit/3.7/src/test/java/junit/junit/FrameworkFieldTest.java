/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runners.model.TestClass;

public class FrameworkFieldTest {
    @Test
    void readsAnnotatedFieldValuesFromATestInstance() {
        TestClass testClass = new TestClass(FieldBackedFixture.class);
        FieldBackedFixture fixture = new FieldBackedFixture();

        List<String> values = testClass.getAnnotatedFieldValues(fixture, FixtureValue.class, String.class);

        assertThat(values).containsExactly("value read through FrameworkField");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface FixtureValue {
    }

    public static final class FieldBackedFixture {
        @FixtureValue
        public final String fieldValue = "value read through FrameworkField";
    }
}
