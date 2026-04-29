/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AspectsTest {

    @Test
    void resolvesSingletonAspectUsingAspectjRuntimeAccessors() {
        MultiScopeAspect aspect = Aspects.aspectOf(MultiScopeAspect.class);

        assertThat(aspect.bindingKind()).isEqualTo(BindingKind.SINGLETON);
        assertThat(aspect.binding()).isNull();
        assertThat(Aspects.hasAspect(MultiScopeAspect.class)).isTrue();
    }

    @Test
    void resolvesPerObjectAspectUsingAspectjRuntimeAccessors() {
        TargetObject target = new TargetObject();

        MultiScopeAspect aspect = Aspects.aspectOf(MultiScopeAspect.class, target);

        assertThat(aspect.bindingKind()).isEqualTo(BindingKind.PER_OBJECT);
        assertThat(aspect.binding()).isSameAs(target);
        assertThat(Aspects.hasAspect(MultiScopeAspect.class, target)).isTrue();
    }

    @Test
    void resolvesPerTypeWithinAspectUsingAspectjRuntimeAccessors() {
        MultiScopeAspect aspect = Aspects.aspectOf(MultiScopeAspect.class, TargetObject.class);

        assertThat(aspect.bindingKind()).isEqualTo(BindingKind.PER_TYPE_WITHIN);
        assertThat(aspect.binding()).isEqualTo(TargetObject.class);
        assertThat(Aspects.hasAspect(MultiScopeAspect.class, TargetObject.class)).isTrue();
    }

    public enum BindingKind {
        SINGLETON,
        PER_OBJECT,
        PER_TYPE_WITHIN
    }

    public static class MultiScopeAspect {
        private static final MultiScopeAspect SINGLETON = new MultiScopeAspect(BindingKind.SINGLETON, null);

        private final BindingKind bindingKind;
        private final Object binding;

        private MultiScopeAspect(BindingKind bindingKind, Object binding) {
            this.bindingKind = bindingKind;
            this.binding = binding;
        }

        public static MultiScopeAspect aspectOf() {
            return SINGLETON;
        }

        public static MultiScopeAspect aspectOf(Object perObject) {
            return new MultiScopeAspect(BindingKind.PER_OBJECT, perObject);
        }

        public static MultiScopeAspect aspectOf(Class<?> perTypeWithin) {
            return new MultiScopeAspect(BindingKind.PER_TYPE_WITHIN, perTypeWithin);
        }

        public static boolean hasAspect() {
            return true;
        }

        public static boolean hasAspect(Object perObject) {
            return perObject instanceof TargetObject;
        }

        public static boolean hasAspect(Class<?> perTypeWithin) {
            return TargetObject.class.equals(perTypeWithin);
        }

        BindingKind bindingKind() {
            return bindingKind;
        }

        Object binding() {
            return binding;
        }
    }

    public static class TargetObject {
    }
}
