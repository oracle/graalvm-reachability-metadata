/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeDescriptionInnerForLoadedTypeTest {
    @Test
    void discoversFieldsAndMemberTypes() {
        TypeDescription owner = TypeDescription.ForLoadedType.of(TestFixtures.Owner.class);
        assertTrue(owner.getDeclaredTypes().getOnly().represents(TestFixtures.Owner.Member.class));
        assertTrue(TestFixtures.sample().getDeclaredFields().filter(named("title")).getOnly()
                .getType().asErasure().represents(String.class));
        assertEquals("net_bytebuddy.byte_buddy", owner.getPackage().getName());
    }
}
