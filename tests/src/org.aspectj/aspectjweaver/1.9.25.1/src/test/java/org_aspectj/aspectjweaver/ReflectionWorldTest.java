/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionWorldTest {
    @Test
    void resolvesAspectAndFindsItsAnnotationThroughDiscoveredFinder() {
        ReflectionWorld world = new ReflectionWorld(getClass().getClassLoader());
        ResolvedType aspect = world.resolve(AnnotatedAspect.class);

        assertThat(aspect.isAspect()).isTrue();
        assertThat(world.getAnnotationFinder().getAnnotationFromClass(
                world.resolve(Aspect.class), AnnotatedAspect.class)).isInstanceOf(Aspect.class);
    }

    @Aspect("perthis(execution(* *(..)))")
    public static class AnnotatedAspect {
    }
}
