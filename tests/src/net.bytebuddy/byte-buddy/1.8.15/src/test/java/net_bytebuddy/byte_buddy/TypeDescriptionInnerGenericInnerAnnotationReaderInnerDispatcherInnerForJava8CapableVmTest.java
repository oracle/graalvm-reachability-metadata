/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void describesAnnotatedReceiverType() {
        TypeDescription.Generic receiver = TestFixtures.method("transform").getReceiverType();
        assertTrue(receiver.asErasure().represents(TestFixtures.Sample.class));
        assertEquals("receiver", TestFixtures.mark(receiver));
        assertEquals("T", receiver.getTypeArguments().getOnly().getSymbol());
    }
}
