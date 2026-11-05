/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;

import static org.assertj.core.api.Assertions.assertThat;

public class BindableRuntimeHintsRegistrarInnerProcessorTest {

    @Test
    void describesJavaBeanConstructionAndPropertyAccessForNativeBinding() {
        RuntimeHints hints = new RuntimeHints();
        BindableRuntimeHintsRegistrar.forBindables(Bindable.of(MailSettings.class).withBindMethod(BindMethod.JAVA_BEAN))
                .registerHints(hints);

        TypeHint mailHints = hints.reflection().getTypeHint(MailSettings.class);
        assertThat(mailHints).isNotNull();
        assertThat(mailHints.constructors()).anySatisfy(constructor -> {
            assertThat(constructor.getParameterTypes()).isEmpty();
            assertThat(constructor.getMode()).isEqualTo(ExecutableMode.INVOKE);
        });
        assertThat(mailHints.methods().map(method -> method.getName())).contains("getSender", "setSender");
    }

    public static class MailSettings {

        private String sender;

        public String getSender() {
            return this.sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }
    }
}
