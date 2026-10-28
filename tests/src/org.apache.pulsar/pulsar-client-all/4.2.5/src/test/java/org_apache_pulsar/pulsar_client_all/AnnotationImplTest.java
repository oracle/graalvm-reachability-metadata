/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.pulsar.shade.javassist.bytecode.ConstPool;
import org.apache.pulsar.shade.javassist.bytecode.annotation.Annotation;
import org.apache.pulsar.shade.javassist.bytecode.annotation.IntegerMemberValue;
import org.apache.pulsar.shade.javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

public class AnnotationImplTest {
    @Test
    void runtimeProxyHasAnnotationEqualityAndHashCodeSemantics() throws Exception {
        ConstPool constPool = new ConstPool(getClass().getName());
        Annotation annotation = new Annotation(RuntimeLabel.class.getName(), constPool);
        annotation.addMemberValue("name", new StringMemberValue("pulsar", constPool));
        annotation.addMemberValue("priority", new IntegerMemberValue(constPool, 4));

        RuntimeLabel proxy = (RuntimeLabel) annotation.toAnnotationType(getClass().getClassLoader(), null);
        RuntimeLabel declared = Labelled.class.getAnnotationsByType(RuntimeLabel.class)[0];

        assertThat(proxy.annotationType()).isEqualTo(RuntimeLabel.class);
        assertThat(proxy.name()).isEqualTo("pulsar");
        assertThat(proxy.priority()).isEqualTo(4);
        assertThat(proxy.hashCode()).isEqualTo(declared.hashCode());
        assertThat(proxy).isEqualTo(declared);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface RuntimeLabel {
        String name();

        int priority();
    }

    @RuntimeLabel(name = "pulsar", priority = 4)
    public static class Labelled {
    }
}
