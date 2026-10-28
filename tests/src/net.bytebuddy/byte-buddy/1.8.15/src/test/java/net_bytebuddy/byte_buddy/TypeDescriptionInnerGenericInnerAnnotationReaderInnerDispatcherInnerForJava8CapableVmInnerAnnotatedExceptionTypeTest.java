/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedExceptionTypeTest {
    @Test
    void readsDeclaredExceptionAnnotation() {
        assertTrue(TestFixtures.method("transform").getExceptionTypes().getOnly().asErasure().represents(IOException.class));
        assertEquals("exception", TestFixtures.mark(TestFixtures.method("transform").getExceptionTypes().getOnly()));
    }
}
