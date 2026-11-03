/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;
import org.jboss.jandex.WildcardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises Jandex's generic type metadata subsystem to build a type-safe application query description.
public class GenericTypeMetadataTest {
    @Test
    @Timeout(60)
    public void discoversGenericRepositoryContract() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(GenericOrderRepository.class);
        Index index = indexer.complete();
        ClassInfo repository = index.getClassByName(DotName.createSimple(GenericOrderRepository.class));

        TypeVariable entityParameter = repository.typeParameters().get(0);
        assertEquals("E", entityParameter.identifier());
        assertEquals(List.of(Order.class.getName()), entityParameter.bounds().stream()
                .map(Type::name)
                .map(Object::toString)
                .toList());

        ParameterizedType repositoryContract = assertInstanceOf(
                ParameterizedType.class, repository.interfaceTypes().get(0));
        assertEquals(Repository.class.getName(), repositoryContract.name().toString());
        assertEquals(List.of("E"), repositoryContract.arguments().stream()
                .map(Type::asTypeVariable)
                .map(TypeVariable::identifier)
                .toList());

        MethodInfo findByIds = repository.firstMethod("findByIds");
        ParameterizedType ids = assertInstanceOf(ParameterizedType.class, findByIds.parameterType(0));
        WildcardType acceptedIds = assertInstanceOf(WildcardType.class, ids.arguments().get(0));
        assertEquals("E", acceptedIds.extendsBound().asTypeVariable().identifier());
        ParameterizedType result = assertInstanceOf(ParameterizedType.class, findByIds.returnType());
        assertEquals(List.of("E"), result.arguments().stream()
                .map(Type::asTypeVariable)
                .map(TypeVariable::identifier)
                .toList());
    }

    private interface Repository<E extends Order> {
        List<E> findByIds(List<? extends E> ids);
    }

    private static final class GenericOrderRepository<E extends Order> implements Repository<E> {
        @Override
        public List<E> findByIds(List<? extends E> ids) {
            return List.copyOf(ids);
        }
    }

    private static final class Order {
    }
}
