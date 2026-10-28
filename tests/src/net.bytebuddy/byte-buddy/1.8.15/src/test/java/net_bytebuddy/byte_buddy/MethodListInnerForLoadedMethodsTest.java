/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodListInnerForLoadedMethodsTest {
    @Test
    void listsConstructorsAndDeclaredMethods() {
        MethodList<MethodDescription.InDefinedShape> methods = new MethodList.ForLoadedMethods(TestFixtures.Sample.class);
        assertEquals(2, methods.filter(isConstructor()).size());
        assertEquals(1, methods.filter(named("transform")).size());
        assertTrue(methods.filter(named("transform")).getOnly().getReturnType().asErasure().represents(String.class));
        assertEquals(1, methods.filter(named("run")).size());
    }
}
