/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.factory.java.JavaComponentFactory;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaComponentFactoryTest {
    @Test
    void createsComponentImplementationWithDefaultConstructor() throws Exception {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(SampleComponent.class.getName());
        descriptor.setImplementation(SampleComponent.class.getName());

        ClassRealm realm = new ClassWorld(
            "java-component-factory-test",
            JavaComponentFactoryTest.class.getClassLoader()
        ).getRealm("java-component-factory-test");

        Object component = new JavaComponentFactory().newInstance(descriptor, realm, null);

        assertThat(component).isInstanceOf(SampleComponent.class);
        assertThat(((SampleComponent) component).message()).isEqualTo("created by JavaComponentFactory");
    }

    public static class SampleComponent {
        public String message() {
            return "created by JavaComponentFactory";
        }
    }
}
