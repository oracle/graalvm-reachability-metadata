/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.sisu.bean.DeclaredMembers;
import org.eclipse.sisu.bean.DeclaredMembers.View;
import org.junit.jupiter.api.Test;

public class DeclaredMembersInnerViewAnonymous1Test {
    @Test
    void constructorsViewIteratesDeclaredConstructors() {
        List<Constructor<?>> constructors = new ArrayList<>();

        for (Member member : new DeclaredMembers(ConstructorFixture.class, View.CONSTRUCTORS)) {
            assertThat(member).isInstanceOf(Constructor.class);
            constructors.add((Constructor<?>) member);
        }

        assertThat(constructors)
            .hasSize(3)
            .extracting(Constructor::getParameterCount)
            .containsExactlyInAnyOrder(0, 1, 2);
    }

    static final class ConstructorFixture {
        ConstructorFixture() {
        }

        private ConstructorFixture(String value) {
            assertThat(value).isNotNull();
        }

        ConstructorFixture(String value, int number) {
            assertThat(value).isNotNull();
            assertThat(number).isNotNegative();
        }
    }
}
