/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class IdentifierGeneratorTest {

    private static final Class[] identifierGenerators = new Class[]{
            org.hibernate.id.UUIDGenerator.class,
            org.hibernate.id.GUIDGenerator.class,
            org.hibernate.id.UUIDHexGenerator.class,
            org.hibernate.id.Assigned.class,
            org.hibernate.id.IdentityGenerator.class,
            org.hibernate.id.SelectGenerator.class,
            org.hibernate.id.enhanced.SequenceStyleGenerator.class,
            org.hibernate.id.IncrementGenerator.class,
            org.hibernate.id.ForeignGenerator.class
    };

    @Test
    public void testIdentifierGenerators() throws Exception {
        for (Class clazz : identifierGenerators) {
            Constructor constructor = clazz.getConstructor();
            assertThat(constructor).isNotNull();
        }
    }
}
