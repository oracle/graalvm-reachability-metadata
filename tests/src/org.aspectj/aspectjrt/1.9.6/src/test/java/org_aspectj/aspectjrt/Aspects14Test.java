/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import org.aspectj.lang.Aspects14;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Aspects14Test {

    @Test
    void resolvesSingletonAspectUsingLegacyAspectjRuntimeAccessors() {
        Aspects14MultiScopeAspect aspect = (Aspects14MultiScopeAspect) Aspects14.aspectOf(Aspects14MultiScopeAspect.class);

        assertThat(aspect.bindingKind()).isEqualTo("singleton");
        assertThat(aspect.binding()).isNull();
        assertThat(Aspects14.hasAspect(Aspects14MultiScopeAspect.class)).isTrue();
    }

    @Test
    void resolvesPerObjectAspectUsingLegacyAspectjRuntimeAccessors() {
        Aspects14TargetObject target = new Aspects14TargetObject();

        Aspects14MultiScopeAspect aspect = (Aspects14MultiScopeAspect) Aspects14.aspectOf(Aspects14MultiScopeAspect.class, target);

        assertThat(aspect.bindingKind()).isEqualTo("per-object");
        assertThat(aspect.binding()).isSameAs(target);
        assertThat(Aspects14.hasAspect(Aspects14MultiScopeAspect.class, target)).isTrue();
    }

    @Test
    void resolvesPerTypeWithinAspectUsingLegacyAspectjRuntimeAccessors() {
        Aspects14MultiScopeAspect aspect = (Aspects14MultiScopeAspect) Aspects14.aspectOf(Aspects14MultiScopeAspect.class, Aspects14TargetObject.class);

        assertThat(aspect.bindingKind()).isEqualTo("per-type-within");
        assertThat(aspect.binding()).isEqualTo(Aspects14TargetObject.class);
        assertThat(Aspects14.hasAspect(Aspects14MultiScopeAspect.class, Aspects14TargetObject.class)).isTrue();
    }
}

class Aspects14MultiScopeAspect {
    private static final Aspects14MultiScopeAspect SINGLETON = new Aspects14MultiScopeAspect("singleton", null);

    private final String bindingKind;
    private final Object binding;

    Aspects14MultiScopeAspect(String bindingKind, Object binding) {
        this.bindingKind = bindingKind;
        this.binding = binding;
    }

    public static Aspects14MultiScopeAspect aspectOf() {
        return SINGLETON;
    }

    public static Aspects14MultiScopeAspect aspectOf(Object perObject) {
        return new Aspects14MultiScopeAspect("per-object", perObject);
    }

    public static Aspects14MultiScopeAspect aspectOf(Class<?> perTypeWithin) {
        return new Aspects14MultiScopeAspect("per-type-within", perTypeWithin);
    }

    public static boolean hasAspect() {
        return true;
    }

    public static boolean hasAspect(Object perObject) {
        return perObject instanceof Aspects14TargetObject;
    }

    public static boolean hasAspect(Class<?> perTypeWithin) {
        return Aspects14TargetObject.class.equals(perTypeWithin);
    }

    String bindingKind() {
        return bindingKind;
    }

    Object binding() {
        return binding;
    }
}

class Aspects14TargetObject {
}
