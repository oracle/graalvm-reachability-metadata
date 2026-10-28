/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypePoolInnerAbstractBaseInnerRawEnumerationValueTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void loadsParsedRetentionPolicy() throws Exception {
        AnnotationDescription retention = TestFixtures.libraryPool().describe(HashCodeAndEqualsPlugin.Enhance.class.getName())
                .resolve().getDeclaredAnnotations().ofType(Retention.class);
        AnnotationValue<?, ?> value = retention.getValue(TestFixtures.property(Retention.class, "value"));
        assertEquals(RetentionPolicy.RUNTIME, value.load(getClass().getClassLoader()).resolve());
        assertEquals(RetentionPolicy.RUNTIME, retention.prepare(Retention.class).load().value());
    }
}
