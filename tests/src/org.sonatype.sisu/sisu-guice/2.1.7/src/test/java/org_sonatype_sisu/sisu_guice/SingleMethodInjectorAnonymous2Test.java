/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

public class SingleMethodInjectorAnonymous2Test {
    @Test
    void invokesPrivateInjectMethodWithResolvedDependency() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Greeting.class).toInstance(new Greeting("hello"));
            }
        });

        PrivateMethodTarget target = injector.getInstance(PrivateMethodTarget.class);

        assertThat(target.message()).isEqualTo("hello from private method");
    }

    public static class PrivateMethodTarget {
        private String message;

        @Inject
        private void initialize(Greeting greeting) {
            message = greeting.value() + " from private method";
        }

        public String message() {
            return message;
        }
    }

    public static class Greeting {
        private final String value;

        public Greeting(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
