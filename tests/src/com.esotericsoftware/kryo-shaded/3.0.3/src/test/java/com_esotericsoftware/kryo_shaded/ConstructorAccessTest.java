/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import com.esotericsoftware.reflectasm.PublicConstructorAccess;
import org.junit.jupiter.api.Test;

public class ConstructorAccessTest {
    @Test
    void createsInstanceUsingPredefinedAccessClass() {
        PredefinedConstructedTypeConstructorAccess predefinedAccess =
                new PredefinedConstructedTypeConstructorAccess();
        assertThat(predefinedAccess.newInstance()).isInstanceOf(PredefinedConstructedType.class);

        ConstructorAccess<PredefinedConstructedType> access =
                ConstructorAccess.get(PredefinedConstructedType.class);

        assertThat(access.isNonStaticMemberClass()).isFalse();
        assertThat(access.newInstance().value).isEqualTo("created by predefined access");
    }

    @Test
    void rejectsTypeWithoutNoArgumentConstructor() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ConstructorAccess.get(TypeWithoutNoArgumentConstructor.class))
                .withMessageContaining("missing no-arg constructor");
    }

    @Test
    void rejectsInnerTypeWithPrivateConstructor() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ConstructorAccess.get(
                        EnclosingType.InnerTypeWithPrivateConstructor.class))
                .withMessageContaining("enclosing class constructor is private");
    }

    public static class PredefinedConstructedType {
        String value;

        public PredefinedConstructedType() {
            value = "created by constructor";
        }
    }

    public static class PredefinedConstructedTypeConstructorAccess extends PublicConstructorAccess {
        @Override
        public Object newInstance() {
            PredefinedConstructedType value = new PredefinedConstructedType();
            value.value = "created by predefined access";
            return value;
        }

        @Override
        public Object newInstance(Object enclosingInstance) {
            throw new UnsupportedOperationException(
                    "PredefinedConstructedType is not an inner type");
        }
    }

    public static class TypeWithoutNoArgumentConstructor {
        public TypeWithoutNoArgumentConstructor(String value) {
        }
    }

    public static class EnclosingType {
        public class InnerTypeWithPrivateConstructor {
            private InnerTypeWithPrivateConstructor() {
            }
        }
    }
}
