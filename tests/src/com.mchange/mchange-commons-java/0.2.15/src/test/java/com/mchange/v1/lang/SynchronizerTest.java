/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v1.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SynchronizerTest {
    @Test
    void createSynchronizedWrapperDelegatesCallsForInterfacesInheritedFromSuperclass() {
        InheritedGreeter target = new InheritedGreeter();
        Greeter synchronizedGreeter = (Greeter) Synchronizer.createSynchronizedWrapper(target);

        String greeting = synchronizedGreeter.greet("metadata");

        assertThat(greeting).isEqualTo("hello metadata");
        assertThat(synchronizedGreeter).isInstanceOf(Greeter.class);
        assertThat(synchronizedGreeter).isNotSameAs(target);
        assertThat(target.invocationCount()).isEqualTo(1);
        assertThat(target.lastName()).isEqualTo("metadata");
    }

    interface Greeter {
        String greet(String name);
    }

    static class BaseGreeter implements Greeter {
        private int invocationCount;
        private String lastName;

        @Override
        public String greet(String name) {
            invocationCount++;
            lastName = name;
            return "hello " + name;
        }

        int invocationCount() {
            return invocationCount;
        }

        String lastName() {
            return lastName;
        }
    }

    private static final class InheritedGreeter extends BaseGreeter {
    }
}
