/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interpolation;

import org.codehaus.plexus.interpolation.reflection.ClassMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapTest {
    @Test
    void mapsPackagePrivateImplementationMethodToPublicInterfaceMethod() throws Exception {
        ClassMap classMap = new ClassMap(ClassMapPackagePrivateSupplier.class);

        Method method = classMap.findMethod("get", new Object[0]);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("get");
        assertThat(method.getDeclaringClass()).isEqualTo(Supplier.class);
    }

    @Test
    void resolvesPublicCounterpartForPackagePrivateDeclaringClass() throws Exception {
        Method implementationMethod = ClassMapPackagePrivateSupplier.class.getMethod("get");

        Method publicMethod = ClassMap.getPublicMethod(implementationMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getName()).isEqualTo("get");
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(Supplier.class);
    }
}

final class ClassMapPackagePrivateSupplier implements Supplier<Object> {
    @Override
    public Object get() {
        return "plexus";
    }
}
