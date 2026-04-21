/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.util.Collections;

import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.functors.InvokerTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokerTransformerTest {

    @Test
    void invokesPublicNoArgMethod() {
        Transformer<GreetingTarget, String> transformer = InvokerTransformer.invokerTransformer("greet");

        String greeting = transformer.transform(new GreetingTarget("metadata"));

        assertThat(greeting).isEqualTo("hello metadata");
    }

    @Test
    void invokesPublicMethodWithArguments() {
        Transformer<GreetingTarget, String> transformer = InvokerTransformer.invokerTransformer(
                "join",
                new Class<?>[]{String.class, Integer.class},
                new Object[]{"-", 3}
        );

        String joined = transformer.transform(new GreetingTarget("meta"));

        assertThat(joined).isEqualTo("meta-meta-meta");
    }

    public static final class GreetingTarget {

        private final String value;

        public GreetingTarget(String value) {
            this.value = value;
        }

        public String greet() {
            return "hello " + value;
        }

        public String join(String separator, Integer copies) {
            return String.join(separator, Collections.nCopies(copies, value));
        }
    }
}
