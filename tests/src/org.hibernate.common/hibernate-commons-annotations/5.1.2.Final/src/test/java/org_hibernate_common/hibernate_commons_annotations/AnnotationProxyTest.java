/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.common.annotationfactory.AnnotationDescriptor;
import org.hibernate.annotations.common.annotationfactory.AnnotationFactory;
import org.junit.jupiter.api.Test;

public class AnnotationProxyTest {
    @Test
    public void createsProxyUsingProvidedAndDefaultAnnotationValues() {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(Deprecated.class);
        descriptor.setValue("since", "hibernate-commons-annotations");

        Deprecated deprecated = AnnotationFactory.create(descriptor);

        assertThat(deprecated.since()).isEqualTo("hibernate-commons-annotations");
        assertThat(deprecated.forRemoval()).isFalse();
    }

    @Test
    public void delegatesAnnotationContractMethodsToAnnotationProxy() {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(Deprecated.class);
        descriptor.setValue("since", "proxy-contract");
        descriptor.setValue("forRemoval", true);

        Deprecated deprecated = AnnotationFactory.create(descriptor);

        assertThat(deprecated.annotationType()).isEqualTo(Deprecated.class);
        assertThat(deprecated.toString())
                .contains("@java.lang.Deprecated(")
                .contains("forRemoval=true")
                .contains("since=proxy-contract");
    }
}
