/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javassist.bytecode.ConstPool;
import org.apache.pulsar.shade.javassist.bytecode.annotation.Annotation;
import org.apache.pulsar.shade.javassist.bytecode.annotation.EnumMemberValue;
import org.junit.jupiter.api.Test;

public class EnumMemberValueTest {
    @Test
    void resolvesEnumConstantThroughRuntimeAnnotation() throws Exception {
        ConstPool constPool = new ConstPool(getClass().getName());
        EnumMemberValue value = new EnumMemberValue(constPool);
        value.setType(Mode.class.getName());
        value.setValue(Mode.PERSISTENT.name());
        Annotation annotation = new Annotation(ModeCarrier.class.getName(), constPool);
        annotation.addMemberValue("value", value);

        ModeCarrier proxy = (ModeCarrier) annotation.toAnnotationType(getClass().getClassLoader(), null);

        assertThat(proxy.value()).isEqualTo(Mode.PERSISTENT);
    }

    public enum Mode {
        PERSISTENT,
        NON_PERSISTENT
    }

    public @interface ModeCarrier {
        Mode value();
    }
}
