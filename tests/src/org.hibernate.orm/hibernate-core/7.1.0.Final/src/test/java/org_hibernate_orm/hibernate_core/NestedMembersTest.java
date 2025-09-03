/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.Arrays;

import org.hibernate.annotations.DialectOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NestedMembersTest {

    @Test
    public void getNestedMembers() {

        Class<?>[] nestMembers = DialectOverride.class.getNestMembers();
        Arrays.stream(nestMembers).forEach(System.out::println);
        if (nestMembers.length != 33) {
            throw new RuntimeException("Expected 33 members but only found %s".formatted(nestMembers.length));
        }
    }
}
