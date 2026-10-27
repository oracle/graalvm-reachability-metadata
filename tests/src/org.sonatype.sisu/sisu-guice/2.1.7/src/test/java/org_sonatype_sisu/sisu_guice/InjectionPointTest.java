/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class InjectionPointTest {
    @Test
    void createsInjectionPointForImplicitNoArgumentConstructor() {
        InjectionPoint injectionPoint = InjectionPoint.forConstructorOf(DefaultConstructible.class);

        assertThat(injectionPoint.getMember()).isInstanceOf(Constructor.class);
        assertThat(injectionPoint.getMember().getDeclaringClass()).isEqualTo(DefaultConstructible.class);
        assertThat(injectionPoint.getDependencies()).isEmpty();
    }

    @Test
    void createsInjectionPointForAnnotatedConstructorDependencies() {
        InjectionPoint injectionPoint = InjectionPoint.forConstructorOf(AnnotatedConstructorTarget.class);

        assertThat(injectionPoint.getMember()).isInstanceOf(Constructor.class);
        assertThat(dependencyKeys(injectionPoint))
                .containsExactly(Key.get(Service.class), Key.get(String.class));
    }

    @Test
    void discoversInstanceFieldsAndMethodsAcrossTypeHierarchy() {
        Set<InjectionPoint> injectionPoints =
                InjectionPoint.forInstanceMethodsAndFields(InjectionTarget.class);

        assertThat(injectionPoints).hasSize(5);
        assertThat(memberNames(injectionPoints))
                .containsExactlyInAnyOrder(
                        "parentService",
                        "initializeParent",
                        "childService",
                        "configure",
                        "initializeChild");
        assertThat(dependencyKeys(injectionPoints))
                .contains(Key.get(Service.class), Key.get(String.class), Key.get(Integer.class));
    }

    @Test
    void discoversStaticFieldsAndMethods() {
        Set<InjectionPoint> injectionPoints =
                InjectionPoint.forStaticMethodsAndFields(StaticInjectionTarget.class);

        assertThat(memberNames(injectionPoints)).containsExactly("staticService", "initializeStatic");
        assertThat(dependencyKeys(injectionPoints))
                .containsExactly(Key.get(Service.class), Key.get(String.class));
    }

    private static List<Key<?>> dependencyKeys(InjectionPoint injectionPoint) {
        List<Key<?>> keys = new ArrayList<Key<?>>();
        for (Dependency<?> dependency : injectionPoint.getDependencies()) {
            keys.add(dependency.getKey());
        }
        return keys;
    }

    private static List<Key<?>> dependencyKeys(Set<InjectionPoint> injectionPoints) {
        List<Key<?>> keys = new ArrayList<Key<?>>();
        for (InjectionPoint injectionPoint : injectionPoints) {
            keys.addAll(dependencyKeys(injectionPoint));
        }
        return keys;
    }

    private static List<String> memberNames(Set<InjectionPoint> injectionPoints) {
        List<String> names = new ArrayList<String>();
        for (InjectionPoint injectionPoint : injectionPoints) {
            Member member = injectionPoint.getMember();
            names.add(member.getName());
        }
        return names;
    }

    static class DefaultConstructible {
    }

    static class AnnotatedConstructorTarget {
        @Inject
        AnnotatedConstructorTarget(Service service, String name) {
        }
    }

    static class ParentInjectionTarget {
        @Inject
        Service parentService;

        @Inject
        void initializeParent(Integer value) {
        }
    }

    static class InjectionTarget extends ParentInjectionTarget {
        @Inject
        Service childService;

        String configure;

        @Inject
        @Named("scala-accessor")
        void configure() {
        }

        @Inject
        void initializeChild(String name) {
        }
    }

    static class StaticInjectionTarget {
        @Inject
        static Service staticService;

        @Inject
        static void initializeStatic(String name) {
        }
    }

    interface Service {
    }
}
