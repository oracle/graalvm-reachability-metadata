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
import org.apache.pulsar.shade.javassist.bytecode.annotation.ClassMemberValue;
import org.junit.jupiter.api.Test;

public class MemberValueTest {
    @Test
    void resolvesClassMemberThroughRuntimeAnnotation() throws Exception {
        ConstPool constPool = new ConstPool(getClass().getName());
        Annotation annotation = new Annotation(TypeCarrier.class.getName(), constPool);
        annotation.addMemberValue("value", new ClassMemberValue(String.class.getName(), constPool));

        TypeCarrier proxy = (TypeCarrier) annotation.toAnnotationType(getClass().getClassLoader(), null);

        assertThat(proxy.value()).isEqualTo(String.class);
    }

    public @interface TypeCarrier {
        Class<?> value();
    }
}
