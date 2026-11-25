/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.aot.AutowiredArgumentsCodeGenerator;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowiredArgumentsCodeGeneratorTest {
    @Test
    void emitsTypedArgumentsForConstructorsWithTheSameArity() {
        AutowiredArgumentsCodeGenerator generator = new AutowiredArgumentsCodeGenerator(
                Label.class, BeanUtils.getResolvableConstructor(Label.class));

        String arguments = generator.generateCode(new Class<?>[] {String.class}).toString();

        assertThat(arguments).isEqualTo("args.get(0, java.lang.String.class)");
    }

    @Test
    void emitsUntypedArgumentsForAnUnambiguousConstructor() {
        AutowiredArgumentsCodeGenerator generator = new AutowiredArgumentsCodeGenerator(
                Shipment.class, BeanUtils.getResolvableConstructor(Shipment.class));

        String arguments = generator.generateCode(
                new Class<?>[] {String.class, int.class}, 1, "resolved").toString();

        assertThat(arguments).isEqualTo("resolved.get(1)");
    }

    public record Label(String text) {
        public Label(CharSequence text) {
            this(text.toString());
        }
    }

    public record Shipment(String destination, int parcels) {
    }
}
