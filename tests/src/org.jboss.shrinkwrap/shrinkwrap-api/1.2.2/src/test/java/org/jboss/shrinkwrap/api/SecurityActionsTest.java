/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.shrinkwrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    @Test
    public void createsInstanceFromResolvedClass() {
        SecurityActionsTest instance = SecurityActions.newInstance(SecurityActionsTest.class,
            new Class<?>[0], new Object[0], SecurityActionsTest.class);

        assertEquals(SecurityActionsTest.class, instance.getClass());
    }

    @Test
    public void createsInstanceFromClassNameAndClassLoader() {
        ClassLoader classLoader = SecurityActionsTest.class.getClassLoader();
        SecurityActionsTest instance = SecurityActions.newInstance(SecurityActionsTest.class.getName(),
            new Class<?>[0], new Object[0], SecurityActionsTest.class, classLoader);

        assertEquals(SecurityActionsTest.class, instance.getClass());
    }
}
