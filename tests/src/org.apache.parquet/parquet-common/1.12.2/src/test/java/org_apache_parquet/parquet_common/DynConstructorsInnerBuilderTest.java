/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_common;

import org.apache.parquet.util.DynConstructors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynConstructorsInnerBuilderTest {
    @Test
    public void implLoadsNamedClassAndFindsPublicConstructor() {
        DynConstructors.Ctor<PublicConstructorSubject> constructor = new DynConstructors.Builder()
                .loader(PublicConstructorSubject.class.getClassLoader())
                .impl(PublicConstructorSubject.class.getName(), String.class, int.class)
                .build();

        assertThat(constructor.getConstructedClass()).isEqualTo(PublicConstructorSubject.class);
    }

    @Test
    public void hiddenImplLoadsNamedClassAndFindsPrivateConstructor() {
        DynConstructors.Ctor<PrivateConstructorSubject> constructor = new DynConstructors.Builder()
                .loader(PrivateConstructorSubject.class.getClassLoader())
                .hiddenImpl(PrivateConstructorSubject.class.getName(), String.class)
                .build();

        assertThat(constructor.getConstructedClass()).isEqualTo(PrivateConstructorSubject.class);
    }
}

final class PublicConstructorSubject {
    public PublicConstructorSubject(String name, int version) {
    }
}

final class PrivateConstructorSubject {
    private PrivateConstructorSubject(String value) {
    }
}
