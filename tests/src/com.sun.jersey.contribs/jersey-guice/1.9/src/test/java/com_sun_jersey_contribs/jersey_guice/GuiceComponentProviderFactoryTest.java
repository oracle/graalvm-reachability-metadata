/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey_contribs.jersey_guice;

import com.google.inject.Inject;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.guice.spi.container.GuiceComponentProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GuiceComponentProviderFactoryTest {
    private final GuiceComponentProviderFactory factory =
            new GuiceComponentProviderFactory(new DefaultResourceConfig(), null);

    @Test
    public void detectsGuiceConstructorInjection() {
        assertThat(factory.isGuiceConstructorInjected(ConstructorInjectedComponent.class)).isTrue();
        assertThat(factory.isImplicitGuiceComponent(ConstructorInjectedComponent.class)).isTrue();
    }

    @Test
    public void detectsGuiceMethodInjection() {
        assertThat(factory.isGuiceFieldOrMethodInjected(MethodInjectedComponent.class)).isTrue();
    }

    @Test
    public void detectsGuiceFieldInjectionAfterInspectingMethods() {
        assertThat(factory.isGuiceFieldOrMethodInjected(FieldInjectedComponent.class)).isTrue();
    }

    @Test
    public void ignoresComponentsWithoutGuiceInjectionPoints() {
        assertThat(factory.isGuiceConstructorInjected(PlainComponent.class)).isFalse();
        assertThat(factory.isGuiceFieldOrMethodInjected(PlainComponent.class)).isFalse();
    }

    public static final class ConstructorInjectedComponent {
        @Inject
        public ConstructorInjectedComponent() {
        }
    }

    public static final class MethodInjectedComponent {
        @Inject
        public void injectDependency(Object dependency) {
        }
    }

    public static final class FieldInjectedComponent {
        @Inject
        private Object dependency;
    }

    public static final class PlainComponent {
    }
}
