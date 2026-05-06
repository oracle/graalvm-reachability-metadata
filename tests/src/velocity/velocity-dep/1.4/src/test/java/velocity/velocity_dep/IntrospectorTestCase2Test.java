/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.velocity.test.IntrospectorTestCase2;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificOverloadAndRejectsAmbiguousMatch() throws Exception {
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase2$Tester");
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase2$Tester2");
        final IntrospectorTestCase2 testCase = new IntrospectorTestCase2("bestMatch");

        testCase.runTest();
    }

    @Test
    void resolvesClassThroughCompilerGeneratedClassLiteralHelper() throws Exception {
        final Method classResolver = IntrospectorTestCase2.class.getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        final Object resolvedClass = classResolver.invoke(
                null, "org.apache.velocity.test.IntrospectorTestCase2$Tester");

        assertThat(resolvedClass).isSameAs(IntrospectorTestCase2.Tester.class);
    }

    private static void clearClassLiteralCache(String fieldName) throws Exception {
        final Field cacheField = IntrospectorTestCase2.class.getDeclaredField(fieldName);
        cacheField.setAccessible(true);
        cacheField.set(null, null);
    }
}
