/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_jpa;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.BeanPath;
import com.querydsl.jpa.JPAExpressions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JPAExpressionsTest {
    @Test
    void treatCreatesSubtypePathWithTreatedMetadata() {
        QAnimal animal = new QAnimal("animal");

        QCat treatedCat = JPAExpressions.treat(animal, QCat.class);

        PathMetadata metadata = treatedCat.getMetadata();
        assertThat(treatedCat).isInstanceOf(QCat.class);
        assertThat(metadata.getParent()).isSameAs(animal);
        assertThat(metadata.getElement()).isEqualTo("JPAExpressionsTest$Cat");
        assertThat(metadata.getPathType()).isEqualTo(PathType.TREATED_PATH);
    }

    public static class Animal {
    }

    public static class Cat extends Animal {
    }

    public static class QAnimal extends BeanPath<Animal> {
        public QAnimal(String variable) {
            super(Animal.class, variable);
        }
    }

    public static class QCat extends BeanPath<Cat> {
        public QCat(PathMetadata metadata) {
            super(Cat.class, metadata);
        }
    }
}
