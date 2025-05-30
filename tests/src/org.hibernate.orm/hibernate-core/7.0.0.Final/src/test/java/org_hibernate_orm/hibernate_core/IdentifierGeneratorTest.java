/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class IdentifierGeneratorTest {

    @ParameterizedTest
    @ValueSource(classes = {
        org.hibernate.id.IdentityGenerator.class,
        org.hibernate.id.SelectGenerator.class,
        org.hibernate.id.enhanced.SequenceStyleGenerator.class,
        org.hibernate.id.IncrementGenerator.class
    })
    public void testIdentifierGenerators(Class<?> identifierGenerator) throws Exception {
        assertThat(identifierGenerator.getConstructor()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {org.hibernate.id.ForeignGenerator.class, org.hibernate.id.Assigned.class,  org.hibernate.id.UUIDGenerator.class,
        org.hibernate.id.GUIDGenerator.class,
        org.hibernate.id.UUIDHexGenerator.class,})
    @SuppressWarnings("deprecation")
    public void testDeprecatedIdentifierGenerators(Class<?> identifierGenerator) throws Exception {
        assertThat(identifierGenerator.getConstructor()).isNotNull();
    }

    /**
     * {@link org.hibernate.annotations.ValueGenerationType#generatedBy()}  may hold types reflectively instantiated.
     * This uses a list of those to make sure hints are present.
     */
    @ParameterizedTest
    @ValueSource(classes = {
        org.hibernate.generator.internal.CurrentTimestampGeneration.class,
        org.hibernate.generator.internal.GeneratedAlwaysGeneration.class,
        org.hibernate.generator.internal.GeneratedGeneration.class,
        org.hibernate.generator.internal.TenantIdGeneration.class,
        org.hibernate.generator.internal.VersionGeneration.class,
        org.hibernate.id.uuid.UuidGenerator.class
    })
    public void testValueGenerationTypes(Class<?> generator) throws Exception {
        for (Constructor<?> ctor : generator.getDeclaredConstructors()) {
            assertThat(generator.getConstructor(ctor.getParameterTypes())).isEqualTo(ctor);
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {
        org.hibernate.generator.internal.SourceGeneration.class
    })
    @SuppressWarnings("deprecation")
    public void testDeprecatedValueGenerationTypes(Class<?> generator) throws Exception {
        for (Constructor<?> ctor : generator.getDeclaredConstructors()) {
            assertThat(generator.getConstructor(ctor.getParameterTypes())).isEqualTo(ctor);
        }
    }
}
