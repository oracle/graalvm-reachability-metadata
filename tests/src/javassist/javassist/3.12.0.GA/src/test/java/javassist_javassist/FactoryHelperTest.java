/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.util.proxy.FactoryHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FactoryHelperTest {
    @Test
    void exposesPrimitiveTypeMetadataUsedByGeneratedProxyBytecode() {
        int booleanIndex = FactoryHelper.typeIndex(Boolean.TYPE);
        int integerIndex = FactoryHelper.typeIndex(Integer.TYPE);
        int longIndex = FactoryHelper.typeIndex(Long.TYPE);
        int doubleIndex = FactoryHelper.typeIndex(Double.TYPE);
        int voidIndex = FactoryHelper.typeIndex(Void.TYPE);

        assertThat(FactoryHelper.wrapperTypes[booleanIndex]).isEqualTo(Boolean.class.getName());
        assertThat(FactoryHelper.wrapperDesc[booleanIndex]).isEqualTo("(Z)V");
        assertThat(FactoryHelper.unwarpMethods[booleanIndex]).isEqualTo("booleanValue");
        assertThat(FactoryHelper.unwrapDesc[booleanIndex]).isEqualTo("()Z");
        assertThat(FactoryHelper.dataSize[booleanIndex]).isOne();

        assertThat(FactoryHelper.wrapperTypes[integerIndex]).isEqualTo(Integer.class.getName());
        assertThat(FactoryHelper.wrapperDesc[integerIndex]).isEqualTo("(I)V");
        assertThat(FactoryHelper.unwarpMethods[integerIndex]).isEqualTo("intValue");
        assertThat(FactoryHelper.unwrapDesc[integerIndex]).isEqualTo("()I");
        assertThat(FactoryHelper.dataSize[integerIndex]).isOne();

        assertThat(FactoryHelper.dataSize[longIndex]).isEqualTo(2);
        assertThat(FactoryHelper.dataSize[doubleIndex]).isEqualTo(2);
        assertThat(FactoryHelper.wrapperTypes[voidIndex]).isEqualTo(Void.class.getName());
    }

    @Test
    void rejectsNonPrimitiveTypeLookup() {
        assertThatThrownBy(() -> FactoryHelper.typeIndex(String.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(String.class.getName());
    }
}
