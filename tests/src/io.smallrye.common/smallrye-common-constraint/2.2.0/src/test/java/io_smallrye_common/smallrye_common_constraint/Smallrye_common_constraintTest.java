/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.smallrye.common.constraint.Assert;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.common.constraint.Nullable;
import org.junit.jupiter.api.Test;

public class Smallrye_common_constraintTest {
    @NotNull
    private static final String REQUIRED_LABEL = "required";

    @Nullable
    private String optionalLabel;

    @Test
    void notNullChecksReturnOriginalValuesAndRejectNulls() {
        String value = "configured";
        Object element = new Object();

        assertThat(Assert.checkNotNullParam("value", value)).isSameAs(value);
        assertThat(Assert.checkNotNullArrayParam("items", 3, element)).isSameAs(element);

        assertThatThrownBy(() -> Assert.checkNotNullParam(null, value))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotNullParam("value", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
        assertThatThrownBy(() -> Assert.checkNotNullArrayParam("items", 3, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items")
                .hasMessageContaining("3");
    }

    @Test
    void notEmptyChecksHandleTextCollectionsMapsAndObjectArrays() {
        String text = "smallrye";
        CharSequence sequence = new StringBuilder("constraint");
        List<String> list = new ArrayList<>(List.of("alpha", "beta"));
        Map<String, Integer> map = new HashMap<>(Map.of("one", 1));
        String[] array = {"first"};

        assertThat(Assert.checkNotEmptyParam("text", text)).isSameAs(text);
        assertThat(Assert.checkNotEmptyParam("sequence", sequence)).isSameAs(sequence);
        assertThat(Assert.checkNotEmptyParam("list", list)).isSameAs(list);
        assertThat(Assert.checkNotEmptyParam("map", map)).isSameAs(map);
        assertThat(Assert.checkNotEmptyParam("array", array)).isSameAs(array);

        assertThatThrownBy(() -> Assert.checkNotEmptyParam("text", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("sequence", (CharSequence) ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequence");
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("list", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list");
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("map", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map");
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("array", new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("array");
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("text", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void notEmptyChecksCoverEveryPrimitiveArrayOverload() {
        boolean[] booleans = {true};
        byte[] bytes = {1};
        short[] shorts = {2};
        int[] ints = {3};
        long[] longs = {4L};
        float[] floats = {5.0f};
        double[] doubles = {6.0d};

        assertThat(Assert.checkNotEmptyParam("booleans", booleans)).isSameAs(booleans);
        assertThat(Assert.checkNotEmptyParam("bytes", bytes)).isSameAs(bytes);
        assertThat(Assert.checkNotEmptyParam("shorts", shorts)).isSameAs(shorts);
        assertThat(Assert.checkNotEmptyParam("ints", ints)).isSameAs(ints);
        assertThat(Assert.checkNotEmptyParam("longs", longs)).isSameAs(longs);
        assertThat(Assert.checkNotEmptyParam("floats", floats)).isSameAs(floats);
        assertThat(Assert.checkNotEmptyParam("doubles", doubles)).isSameAs(doubles);

        assertThatThrownBy(() -> Assert.checkNotEmptyParam("booleans", new boolean[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("bytes", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("shorts", new short[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("ints", new int[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("longs", new long[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("floats", new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Assert.checkNotEmptyParam("doubles", new double[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minimumAndMaximumChecksAcceptBoundaryValuesAndRejectOutOfRangeValues() {
        assertThatCode(() -> Assert.checkMinimumParameter("letter", "m", "m")).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMinimumParameter("letter", "m", "z")).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameter("letter", "m", "a")).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameter("letter", "m", "m")).doesNotThrowAnyException();
        assertThatThrownBy(() -> Assert.checkMinimumParameter("letter", "m", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letter");
        assertThatThrownBy(() -> Assert.checkMaximumParameter("letter", "m", "z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letter");

        assertNumericMinimums();
        assertNumericMaximums();
    }

    @Test
    void unsignedBoundsUseUnsignedIntegerAndLongOrdering() {
        assertThatCode(() -> Assert.checkMinimumParameterUnsigned("unsignedInt", -2, -1)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMinimumParameterUnsigned("unsignedLong", -2L, -1L)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameterUnsigned("unsignedInt", 1, 0)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameterUnsigned("unsignedLong", 1L, 0L)).doesNotThrowAnyException();

        assertThatThrownBy(() -> Assert.checkMinimumParameterUnsigned("unsignedInt", -2, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsignedInt");
        assertThatThrownBy(() -> Assert.checkMinimumParameterUnsigned("unsignedLong", -2L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsignedLong");
        assertThatThrownBy(() -> Assert.checkMaximumParameterUnsigned("unsignedInt", 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsignedInt");
        assertThatThrownBy(() -> Assert.checkMaximumParameterUnsigned("unsignedLong", 1L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsignedLong");
    }

    @Test
    void powerOfTwoChecksAcceptZeroAndSingleBitValuesOnly() {
        assertThatCode(() -> Assert.checkPow2Parameter("intPower", 0)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("intPower", 1)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("intPower", 1 << 20)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("longPower", 0L)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("longPower", 1L)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("longPower", 1L << 40)).doesNotThrowAnyException();

        assertThatThrownBy(() -> Assert.checkPow2Parameter("intPower", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intPower");
        assertThatThrownBy(() -> Assert.checkPow2Parameter("longPower", 12L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longPower");
    }

    @Test
    void powerOfTwoChecksUseUnsignedSingleBitSemantics() {
        assertThatCode(() -> Assert.checkPow2Parameter("intPower", Integer.MIN_VALUE)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkPow2Parameter("longPower", Long.MIN_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void arrayBoundsChecksValidateTypedArraysOffsetsAndLengths() {
        String[] objects = {"a", "b", "c"};
        byte[] bytes = {1, 2, 3};
        char[] chars = {'a', 'b', 'c'};
        int[] ints = {1, 2, 3};
        long[] longs = {1L, 2L, 3L};

        assertThatCode(() -> Assert.checkArrayBounds(objects, 1, 2)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkArrayBounds(bytes, 0, 3)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkArrayBounds(chars, 2, 1)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkArrayBounds(ints, 3, 0)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkArrayBounds(longs, 0, 0)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkArrayBounds(3, 1, 2)).doesNotThrowAnyException();

        assertThatThrownBy(() -> Assert.checkArrayBounds(objects, 4, 0))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> Assert.checkArrayBounds(bytes, 2, 2))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> Assert.checkArrayBounds(3, -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offs");
        assertThatThrownBy(() -> Assert.checkArrayBounds(3, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("len");
        assertThatThrownBy(() -> Assert.checkArrayBounds((Object[]) null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("array");
    }

    @Test
    void assertionHelpersReturnValidatedValuesWhenContractsHold() {
        Object monitor = new Object();

        assertThat(Assert.assertNotNull(REQUIRED_LABEL)).isSameAs(REQUIRED_LABEL);
        assertThat(Assert.assertTrue(true)).isTrue();
        assertThat(Assert.assertFalse(false)).isFalse();
        synchronized (monitor) {
            assertThat(Assert.assertHoldsLock(monitor)).isSameAs(monitor);
        }
        assertThat(Assert.assertNotHoldsLock(monitor)).isSameAs(monitor);
    }

    @Test
    void exceptionFactoriesReturnSpecificExceptionTypesWithDiagnosticMessages() {
        IllegalStateException unreachable = Assert.unreachableCode();
        IllegalStateException objectCase = Assert.impossibleSwitchCase("blue");
        IllegalStateException charCase = Assert.impossibleSwitchCase('x');
        IllegalStateException intCase = Assert.impossibleSwitchCase(7);
        IllegalStateException longCase = Assert.impossibleSwitchCase(9L);
        UnsupportedOperationException unsupported = unsupportedFromHelper();

        assertThat(unreachable).hasMessageContaining("Unreachable");
        assertThat(objectCase).hasMessageContaining("blue");
        assertThat(charCase).hasMessageContaining("x");
        assertThat(intCase).hasMessageContaining("7");
        assertThat(longCase).hasMessageContaining("9");
        assertThat(unsupported).hasMessageContaining("not supported");
        assertThatThrownBy(() -> Assert.impossibleSwitchCase((Object) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obj");
    }

    @Test
    void unsupportedFactoryReportsTheCallingMethodAndClass() {
        UnsupportedOperationException unsupported = unsupportedFromNamedCaller();

        assertThat(unsupported)
                .hasMessageContaining("unsupportedFromNamedCaller")
                .hasMessageContaining(Smallrye_common_constraintTest.class.getName());
    }

    @Test
    void nullableAndNotNullAnnotationsCanBeUsedOnPublicContractsAndLocalVariables() {
        optionalLabel = null;
        String first = normalizeLabel(REQUIRED_LABEL, optionalLabel);

        optionalLabel = "custom";
        @Nullable String nullableLocal = optionalLabel;
        @NotNull String second = normalizeLabel(REQUIRED_LABEL, nullableLocal);

        assertThat(first).isEqualTo("required");
        assertThat(second).isEqualTo("custom");
    }

    @NotNull
    private static String normalizeLabel(@NotNull String fallback, @Nullable String candidate) {
        return candidate == null ? fallback : candidate;
    }

    private static void assertNumericMinimums() {
        assertThatCode(() -> Assert.checkMinimumParameter("intValue", 10, 10)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMinimumParameter("longValue", 10L, 11L)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMinimumParameter("floatValue", 1.5f, 1.5f)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMinimumParameter("doubleValue", 1.5d, 2.5d)).doesNotThrowAnyException();

        assertThatThrownBy(() -> Assert.checkMinimumParameter("intValue", 10, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intValue");
        assertThatThrownBy(() -> Assert.checkMinimumParameter("longValue", 10L, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longValue");
        assertThatThrownBy(() -> Assert.checkMinimumParameter("floatValue", 1.5f, 1.4f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floatValue");
        assertThatThrownBy(() -> Assert.checkMinimumParameter("doubleValue", 1.5d, 1.4d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doubleValue");
    }

    private static void assertNumericMaximums() {
        assertThatCode(() -> Assert.checkMaximumParameter("intValue", 10, 10)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameter("longValue", 10L, 9L)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameter("floatValue", 1.5f, 1.5f)).doesNotThrowAnyException();
        assertThatCode(() -> Assert.checkMaximumParameter("doubleValue", 1.5d, 1.4d)).doesNotThrowAnyException();

        assertThatThrownBy(() -> Assert.checkMaximumParameter("intValue", 10, 11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intValue");
        assertThatThrownBy(() -> Assert.checkMaximumParameter("longValue", 10L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longValue");
        assertThatThrownBy(() -> Assert.checkMaximumParameter("floatValue", 1.5f, 1.6f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floatValue");
        assertThatThrownBy(() -> Assert.checkMaximumParameter("doubleValue", 1.5d, 1.6d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doubleValue");
    }

    private static UnsupportedOperationException unsupportedFromHelper() {
        return Assert.unsupported();
    }

    private static UnsupportedOperationException unsupportedFromNamedCaller() {
        return Assert.unsupported();
    }
}
