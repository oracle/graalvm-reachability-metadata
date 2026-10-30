/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.auxiliary.PrivilegedMemberLookupAction;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PrivilegedMemberLookupActionTest {
    public static class Members {
        public Members() {
        }

        private Members(String name) {
        }

        public String visible() {
            return "public";
        }

        private String hidden() {
            return "private";
        }
    }

    @Test
    void selectsLookupActionByMemberKindAndVisibility() {
        TypeDescription type = TypeDescription.ForLoadedType.of(Members.class);
        MethodDescription publicConstructor = type.getDeclaredMethods().filter(isConstructor().and(takesArguments(0))).getOnly();
        MethodDescription privateConstructor = type.getDeclaredMethods().filter(isConstructor().and(takesArguments(String.class))).getOnly();
        assertSame(PrivilegedMemberLookupAction.FOR_PUBLIC_CONSTRUCTOR, PrivilegedMemberLookupAction.of(publicConstructor));
        assertSame(PrivilegedMemberLookupAction.FOR_DECLARED_CONSTRUCTOR, PrivilegedMemberLookupAction.of(privateConstructor));
        assertSame(PrivilegedMemberLookupAction.FOR_PUBLIC_METHOD,
                PrivilegedMemberLookupAction.of(type.getDeclaredMethods().filter(named("visible")).getOnly()));
        assertSame(PrivilegedMemberLookupAction.FOR_DECLARED_METHOD,
                PrivilegedMemberLookupAction.of(type.getDeclaredMethods().filter(named("hidden")).getOnly()));
    }
}
