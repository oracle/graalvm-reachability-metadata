/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_atteo_classindex.classindex;

import static org.assertj.core.api.Assertions.assertThat;

import org.atteo.classindex.ClassFilter;
import org.junit.jupiter.api.Test;

public class ClassFilterInnerBuilderAnonymous8Test {
    @Test
    void matchesClassesWithPublicDefaultConstructor() {
        ClassFilter.UnionBuilder filter = ClassFilter.only().withPublicDefaultConstructor();

        assertThat(filter.matches(ClassFilterInnerBuilderAnonymous8Test.class)).isTrue();
    }

    @Test
    void rejectsTypesWithoutPublicDefaultConstructor() {
        ClassFilter.UnionBuilder filter = ClassFilter.only().withPublicDefaultConstructor();

        assertThat(filter.matches(ClassFilter.Predicate.class)).isFalse();
    }
}
