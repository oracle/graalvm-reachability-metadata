/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_qual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.PatternSyntaxException;
import org.checkerframework.checker.formatter.FormatUtil;
import org.checkerframework.checker.formatter.FormatUtil.ExcessiveOrMissingFormatArgumentException;
import org.checkerframework.checker.formatter.FormatUtil.IllegalFormatConversionCategoryException;
import org.checkerframework.checker.formatter.qual.ConversionCategory;
import org.checkerframework.checker.formatter.qual.Format;
import org.checkerframework.checker.i18nformatter.I18nFormatUtil;
import org.checkerframework.checker.i18nformatter.qual.I18nConversionCategory;
import org.checkerframework.checker.i18nformatter.qual.I18nFormat;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.NullnessUtil;
import org.checkerframework.checker.nullness.Opt;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.optional.qual.Present;
import org.checkerframework.checker.regex.RegexUtil;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.checker.signedness.SignednessUtil;
import org.checkerframework.checker.signedness.qual.Unsigned;
import org.checkerframework.checker.tainting.qual.Untainted;
import org.checkerframework.checker.units.UnitsTools;
import org.junit.jupiter.api.Test;

class Checker_qualTest {
    @Test
    void representativeAnnotationUsageCompilesAndExecutes() {
        AnnotatedApiUsage usage = new AnnotatedApiUsage("checker", null, "clean", (byte) 0x7f);

        assertThat(usage.requiredValue()).isEqualTo("checker");
        assertThat(usage.optionalValue()).isEqualTo("fallback");
        assertThat(usage.groupedPattern()).isEqualTo("(checker)");
        assertThat(usage.printfPattern()).isEqualTo("%s:%d");
        assertThat(usage.messagePattern()).isEqualTo("{0,number,integer} on {1,date,short}");
        assertThat(usage.rawByteValue()).isEqualTo((byte) 0x7f);
        assertThat(usage.cleanValue()).isEqualTo("clean");
    }

    @Test
    void formatUtilValidatesPrintfPatterns() {
        String relativeAndIndexedFormat = "%1$s %<S %2$d %3$tY %3$tm";
        ConversionCategory[] categories = FormatUtil.formatParameterCategories(relativeAndIndexedFormat);

        assertThat(conversionNames(categories)).containsExactly("GENERAL", "INT", "TIME");
        assertThat(FormatUtil.asFormat(relativeAndIndexedFormat,
                ConversionCategory.GENERAL,
                ConversionCategory.INT,
                ConversionCategory.TIME)).isEqualTo(relativeAndIndexedFormat);
        assertThat(conversionNames(FormatUtil.formatParameterCategories("%2$s")))
                .containsExactly("UNUSED", "GENERAL");
        assertThat(FormatUtil.asFormat("%2$s", ConversionCategory.UNUSED, ConversionCategory.GENERAL))
                .isEqualTo("%2$s");

        ExcessiveOrMissingFormatArgumentException argumentException = assertThrows(
                ExcessiveOrMissingFormatArgumentException.class,
                () -> FormatUtil.asFormat("%s %d", ConversionCategory.GENERAL));
        assertThat(argumentException.getExpected()).isEqualTo(1);
        assertThat(argumentException.getFound()).isEqualTo(2);
        assertThat(argumentException.getMessage()).isEqualTo("Expected 1 arguments but found 2.");

        IllegalFormatConversionCategoryException categoryException = assertThrows(
                IllegalFormatConversionCategoryException.class,
                () -> FormatUtil.asFormat("%d", ConversionCategory.FLOAT));
        assertThat(categoryException.getExpected()).isEqualTo(ConversionCategory.FLOAT);
        assertThat(categoryException.getFound()).isEqualTo(ConversionCategory.INT);
        assertThat(categoryException.getMessage())
                .isEqualTo("Expected category FLOAT conversion category (one of: float, double, BigDecimal) "
                        + "but found INT conversion category (one of: byte, short, int, long, BigInteger).");
    }

    @Test
    void conversionCategoryHelpersHandleSetOperations() {
        assertThat(ConversionCategory.fromConversionChar('x')).isEqualTo(ConversionCategory.INT);
        assertThat(ConversionCategory.fromConversionChar('T')).isEqualTo(ConversionCategory.TIME);
        assertThat(ConversionCategory.intersect(ConversionCategory.CHAR, ConversionCategory.INT))
                .isEqualTo(ConversionCategory.CHAR_AND_INT);
        assertThat(ConversionCategory.union(ConversionCategory.CHAR_AND_INT, ConversionCategory.INT_AND_TIME))
                .isEqualTo(ConversionCategory.INT);
        assertThat(ConversionCategory.isSubsetOf(ConversionCategory.CHAR_AND_INT, ConversionCategory.INT)).isTrue();
        assertThat(ConversionCategory.FLOAT.toString())
                .isEqualTo("FLOAT conversion category (one of: float, double, BigDecimal)");
        assertThatThrownBy(() -> ConversionCategory.fromConversionChar('?'))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bad conversion character ?");
    }

    @Test
    void i18nFormatUtilitiesUnderstandMessageFormatPatterns() {
        String pattern = "{0,number,integer} paid on {1,date,short} and {2}";
        I18nConversionCategory[] categories = I18nFormatUtil.formatParameterCategories(pattern);

        assertThat(i18nConversionNames(categories)).containsExactly("NUMBER", "DATE", "GENERAL");
        assertThat(I18nFormatUtil.hasFormat(
                pattern,
                I18nConversionCategory.NUMBER,
                I18nConversionCategory.DATE,
                I18nConversionCategory.GENERAL)).isTrue();
        assertThat(I18nFormatUtil.hasFormat(
                pattern,
                I18nConversionCategory.DATE,
                I18nConversionCategory.DATE,
                I18nConversionCategory.GENERAL)).isFalse();
        assertThat(I18nFormatUtil.isFormat(pattern)).isTrue();
        assertThat(I18nFormatUtil.isFormat("broken {0,number")).isFalse();
        assertThat(I18nConversionCategory.stringToI18nConversionCategory("TIME"))
                .isEqualTo(I18nConversionCategory.DATE);
        assertThat(I18nConversionCategory.stringToI18nConversionCategory("choice"))
                .isEqualTo(I18nConversionCategory.NUMBER);
        assertThat(I18nConversionCategory.intersect(I18nConversionCategory.DATE, I18nConversionCategory.NUMBER))
                .isEqualTo(I18nConversionCategory.NUMBER);
        assertThat(I18nConversionCategory.union(I18nConversionCategory.DATE, I18nConversionCategory.NUMBER))
                .isEqualTo(I18nConversionCategory.DATE);
        assertThat(I18nConversionCategory.isSubsetOf(I18nConversionCategory.NUMBER, I18nConversionCategory.DATE))
                .isTrue();
        assertThat(I18nConversionCategory.NUMBER.toString())
                .isEqualTo("NUMBER conversion category (one of: java.lang.Number)");
        assertThatThrownBy(() -> I18nConversionCategory.stringToI18nConversionCategory("currency"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid format type currency");
    }

    @Test
    void optAndNullnessUtilitiesModelOptionalAndNullContracts() {
        String value = "value";
        AtomicReference<String> seen = new AtomicReference<>();
        String[] oneDimensional = new String[] {"alpha", "beta"};
        String[][] twoDimensional = new String[][] {{"gamma"}, {"delta"}};
        String[][][] threeDimensional = new String[][][] {{{"epsilon"}}};
        String[][][][] fourDimensional = new String[][][][] {{{{"zeta"}}}};
        String[][][][][] fiveDimensional = new String[][][][][] {{{{{"eta"}}}}};

        assertThat(Opt.get(value)).isEqualTo(value);
        assertThatThrownBy(() -> Opt.get(null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("No value present");
        assertThat(Opt.isPresent(value)).isTrue();
        assertThat(Opt.isPresent(null)).isFalse();
        Opt.ifPresent(value, seen::set);
        Opt.ifPresent(null, ignored -> seen.set("changed"));
        assertThat(seen.get()).isEqualTo(value);
        assertThat(Opt.filter(value, candidate -> candidate.startsWith("val"))).isEqualTo(value);
        assertThat(Opt.filter(value, candidate -> candidate.startsWith("x"))).isNull();
        assertThat(Opt.map(value, String::length)).isEqualTo(5);
        assertThat(Opt.map(null, String::length)).isNull();
        assertThat(Opt.orElse(value, "fallback")).isEqualTo(value);
        assertThat(Opt.orElse(null, "fallback")).isEqualTo("fallback");
        assertThat(Opt.orElseGet(value, () -> "generated")).isEqualTo(value);
        assertThat(Opt.orElseGet(null, () -> "generated")).isEqualTo("generated");
        assertThat(Opt.orElseThrow(value, IllegalStateException::new)).isEqualTo(value);
        assertThatThrownBy(() -> Opt.orElseThrow(null, () -> new IllegalStateException("missing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing");

        assertThat(NullnessUtil.castNonNull(value)).isEqualTo(value);
        assertThat(NullnessUtil.castNonNullDeep(oneDimensional)).containsExactly("alpha", "beta");
        assertThat(NullnessUtil.castNonNullDeep(twoDimensional)[1]).containsExactly("delta");
        assertThat(NullnessUtil.castNonNullDeep(threeDimensional)[0][0]).containsExactly("epsilon");
        assertThat(NullnessUtil.castNonNullDeep(fourDimensional)[0][0][0]).containsExactly("zeta");
        assertThat(NullnessUtil.castNonNullDeep(fiveDimensional)[0][0][0][0]).containsExactly("eta");
    }

    @Test
    void regexUtilitiesValidatePatternsAndGroupCounts() {
        String groupedPattern = "(left)(right)";
        String insufficientGroupPattern = "(left)";
        String invalidPattern = "[";

        assertThat(RegexUtil.isRegex(groupedPattern)).isTrue();
        assertThat(RegexUtil.isRegex(groupedPattern, 2)).isTrue();
        assertThat(RegexUtil.isRegex(insufficientGroupPattern, 2)).isFalse();
        assertThat(RegexUtil.isRegex('a')).isTrue();
        assertThat(RegexUtil.isRegex('(')).isFalse();
        assertThat(RegexUtil.regexError(insufficientGroupPattern, 2))
                .isEqualTo("regex \"(left)\" has 1 groups, but 2 groups are needed.");
        assertThat(RegexUtil.regexError(invalidPattern)).contains("Unclosed character class");
        assertThat(RegexUtil.regexException(insufficientGroupPattern, 2))
                .hasMessageContaining("regex \"(left)\" has 1 groups, but 2 groups are needed.");
        assertThat(RegexUtil.regexException(invalidPattern)).isInstanceOf(PatternSyntaxException.class);
        assertThat(RegexUtil.asRegex(groupedPattern, 2)).isEqualTo(groupedPattern);
        assertThatThrownBy(() -> RegexUtil.asRegex(insufficientGroupPattern, 2))
                .isInstanceOf(Error.class)
                .hasMessage("regex \"(left)\" has 1 groups, but 2 groups are needed.");
        assertThatThrownBy(() -> RegexUtil.asRegex(invalidPattern))
                .isInstanceOf(Error.class)
                .hasCauseInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void signednessUtilitiesSupportBuffersAndFiles() throws Exception {
        ByteBuffer wrapped = SignednessUtil.wrapUnsigned(new byte[] {(byte) 0xff, 0x00, 0x7f, 0x55});
        ByteBuffer wrappedSlice = SignednessUtil.wrapUnsigned(new byte[] {10, 20, 30, 40}, 1, 2);
        ByteBuffer sequential = ByteBuffer.allocate(8);
        ByteBuffer indexed = ByteBuffer.allocate(16);
        ByteBuffer bulkSource = SignednessUtil.wrapUnsigned(new byte[] {5, 6, 7});
        IntBuffer indexedInts = IntBuffer.allocate(4);
        IntBuffer copiedInts = IntBuffer.allocate(3);
        IntBuffer rangedInts = IntBuffer.allocate(2);
        byte[] bulkCopy = new byte[3];
        byte[] rangedCopy = new byte[2];
        Path tempFile = Files.createTempFile("checker-qual-signedness", ".bin");

        assertThat(SignednessUtil.getUnsigned(wrapped)).isEqualTo((byte) 0xff);
        assertThat(SignednessUtil.getUnsigned(wrapped, 2)).isEqualTo((byte) 0x7f);
        wrapped.position(1);
        SignednessUtil.getUnsigned(wrapped, rangedCopy, 0, 2);
        assertThat(rangedCopy).containsExactly((byte) 0x00, (byte) 0x7f);
        assertThat(wrappedSlice.position()).isEqualTo(1);
        assertThat(wrappedSlice.remaining()).isEqualTo(2);

        SignednessUtil.putUnsigned(sequential, (byte) 0x01);
        SignednessUtil.putUnsignedShort(sequential, (short) 0x0203);
        SignednessUtil.putUnsignedInt(sequential, 0x04050607);
        sequential.flip();
        assertThat(SignednessUtil.getUnsigned(sequential)).isEqualTo((byte) 0x01);
        assertThat(SignednessUtil.getUnsignedShort(sequential)).isEqualTo((short) 0x0203);
        assertThat(SignednessUtil.getUnsignedInt(sequential)).isEqualTo(0x04050607);

        SignednessUtil.putUnsigned(indexed, 0, (byte) 0x55);
        SignednessUtil.putUnsignedShort(indexed, 1, (short) 0x1234);
        SignednessUtil.putUnsignedInt(indexed, 4, 0xcafebabe);
        SignednessUtil.putUnsignedLong(indexed, 8, 0x0102030405060708L);
        assertThat(SignednessUtil.getUnsigned(indexed, 0)).isEqualTo((byte) 0x55);
        assertThat(indexed.getShort(1)).isEqualTo((short) 0x1234);
        assertThat(indexed.getInt(4)).isEqualTo(0xcafebabe);
        assertThat(indexed.getLong(8)).isEqualTo(0x0102030405060708L);

        SignednessUtil.getUnsigned(bulkSource, bulkCopy);
        assertThat(bulkCopy).containsExactly((byte) 5, (byte) 6, (byte) 7);

        SignednessUtil.putUnsigned(indexedInts, 99);
        SignednessUtil.putUnsigned(indexedInts, 1, 123);
        assertThat(SignednessUtil.getUnsigned(indexedInts, 1)).isEqualTo(123);
        indexedInts.flip();
        assertThat(indexedInts.get()).isEqualTo(99);

        SignednessUtil.putUnsigned(copiedInts, new int[] {4, 5, 6});
        copiedInts.flip();
        assertThat(copiedInts.get()).isEqualTo(4);
        assertThat(copiedInts.get()).isEqualTo(5);
        assertThat(copiedInts.get()).isEqualTo(6);

        SignednessUtil.putUnsigned(rangedInts, new int[] {7, 8, 9}, 1, 2);
        rangedInts.flip();
        assertThat(rangedInts.get()).isEqualTo(8);
        assertThat(rangedInts.get()).isEqualTo(9);

        try (RandomAccessFile file = new RandomAccessFile(tempFile.toFile(), "rw")) {
            SignednessUtil.writeUnsigned(file, new byte[] {10, 20, 30, 40}, 1, 2);
            file.seek(0);
            assertThat(SignednessUtil.readUnsigned(file, rangedCopy, 0, 2)).isEqualTo(2);
            assertThat(rangedCopy).containsExactly((byte) 20, (byte) 30);

            file.setLength(0);
            file.seek(0);
            SignednessUtil.writeUnsigned(file, new byte[] {1, 2, 3}, 0, 3);
            file.seek(0);
            SignednessUtil.readFullyUnsigned(file, bulkCopy);
            assertThat(bulkCopy).containsExactly((byte) 1, (byte) 2, (byte) 3);

            file.setLength(0);
            file.seek(0);
            SignednessUtil.writeUnsignedByte(file, (byte) 0xff);
            SignednessUtil.writeUnsignedChar(file, 'A');
            SignednessUtil.writeUnsignedInt(file, 0xcafebabe);
            SignednessUtil.writeUnsignedLong(file, 0x0102030405060708L);
            file.seek(0);
            assertThat(file.readUnsignedByte()).isEqualTo(255);
            assertThat(SignednessUtil.readUnsignedChar(file)).isEqualTo('A');
            assertThat(SignednessUtil.readUnsignedInt(file)).isEqualTo(0xcafebabe);
            assertThat(SignednessUtil.readUnsignedLong(file)).isEqualTo(0x0102030405060708L);

            file.setLength(0);
            file.seek(0);
            SignednessUtil.writeUnsignedShort(file, (short) 0x1234);
            file.seek(0);
            assertThat(file.readUnsignedShort()).isEqualTo(0x1234);
        }

        Files.deleteIfExists(tempFile);
    }

    @Test
    void signednessUtilitiesExposeUnsignedViewsAndPrimitiveConversions() {
        char maxUnsignedByteAsChar = '\u00ff';
        double expectedUnsignedLong = new BigInteger("18446744073709551615").doubleValue();

        assertThat(SignednessUtil.compareUnsigned((byte) 0xff, (byte) 0x01)).isPositive();
        assertThat(SignednessUtil.compareUnsigned((short) 0xffff, (short) 0x0001)).isPositive();
        assertThat(SignednessUtil.toUnsignedString((byte) 0xff)).isEqualTo("255");
        assertThat(SignednessUtil.toUnsignedString((byte) 0xff, 16)).isEqualTo("ff");
        assertThat(SignednessUtil.toUnsignedString((short) 0xffff)).isEqualTo("65535");
        assertThat(SignednessUtil.toUnsignedString((short) 0xffff, 16)).isEqualTo("ffff");
        assertThat((int) SignednessUtil.toUnsignedShort((byte) 0xff)).isEqualTo(255);
        assertThat(SignednessUtil.toUnsignedLong(maxUnsignedByteAsChar)).isEqualTo(255L);
        assertThat(SignednessUtil.toUnsignedInt(maxUnsignedByteAsChar)).isEqualTo(255);
        assertThat((int) SignednessUtil.toUnsignedShort(maxUnsignedByteAsChar)).isEqualTo(255);
        assertThat(SignednessUtil.toFloat((byte) 0xff)).isEqualTo(255.0f);
        assertThat(SignednessUtil.toFloat((short) 0xffff)).isEqualTo(65535.0f);
        assertThat(SignednessUtil.toFloat(-1)).isGreaterThan(4.0e9f);
        assertThat(SignednessUtil.toFloat(-1L)).isGreaterThan(1.8e19f);
        assertThat(SignednessUtil.toDouble((byte) 0xff)).isEqualTo(255.0d);
        assertThat(SignednessUtil.toDouble((short) 0xffff)).isEqualTo(65535.0d);
        assertThat(SignednessUtil.toDouble(-1)).isEqualTo(4294967295.0d);
        assertThat(SignednessUtil.toDouble(-1L)).isEqualTo(expectedUnsignedLong);
        assertThat(SignednessUtil.byteFromFloat(255.0f)).isEqualTo((byte) 0xff);
        assertThat(SignednessUtil.shortFromFloat(65535.0f)).isEqualTo((short) 0xffff);
        assertThat(SignednessUtil.intFromFloat(12345.0f)).isEqualTo(12345);
        assertThat(SignednessUtil.longFromFloat(12345.0f)).isEqualTo(12345L);
        assertThat(SignednessUtil.byteFromDouble(128.0d)).isEqualTo((byte) 0x80);
        assertThat(SignednessUtil.shortFromDouble(32768.0d)).isEqualTo((short) 0x8000);
        assertThat(SignednessUtil.intFromDouble(67890.0d)).isEqualTo(67890);
        assertThat(SignednessUtil.longFromDouble(67890.0d)).isEqualTo(67890L);
    }

    @Test
    void unitsToolsConvertBetweenSupportedUnits() {
        UnitsTools unitsTools = new UnitsTools();

        assertThat(unitsTools).isNotNull();
        assertThat(UnitsTools.toRadians(180.0d)).isCloseTo(Math.PI, offset(1.0e-12));
        assertThat(UnitsTools.toDegrees(Math.PI)).isCloseTo(180.0d, offset(1.0e-12));
        assertThat(UnitsTools.fromMilliMeterToMeter(2500)).isEqualTo(2);
        assertThat(UnitsTools.fromMeterToMilliMeter(3)).isEqualTo(3000);
        assertThat(UnitsTools.fromMeterToKiloMeter(2500)).isEqualTo(2);
        assertThat(UnitsTools.fromKiloMeterToMeter(3)).isEqualTo(3000);
        assertThat(UnitsTools.fromGramToKiloGram(2500)).isEqualTo(2);
        assertThat(UnitsTools.fromKiloGramToGram(3)).isEqualTo(3000);
        assertThat(UnitsTools.fromMeterPerSecondToKiloMeterPerHour(10.0d)).isEqualTo(36.0d);
        assertThat(UnitsTools.fromKiloMeterPerHourToMeterPerSecond(36.0d)).isEqualTo(10.0d);
        assertThat(UnitsTools.fromKelvinToCelsius(300)).isEqualTo(27);
        assertThat(UnitsTools.fromCelsiusToKelvin(27)).isEqualTo(300);
        assertThat(UnitsTools.fromSecondToMinute(180)).isEqualTo(3);
        assertThat(UnitsTools.fromMinuteToSecond(3)).isEqualTo(180);
        assertThat(UnitsTools.fromMinuteToHour(180)).isEqualTo(3);
        assertThat(UnitsTools.fromHourToMinute(3)).isEqualTo(180);
    }

    private static List<String> conversionNames(ConversionCategory[] categories) {
        List<String> names = new ArrayList<>(categories.length);
        for (ConversionCategory category : categories) {
            names.add(category.name());
        }
        return names;
    }

    private static List<String> i18nConversionNames(I18nConversionCategory[] categories) {
        List<String> names = new ArrayList<>(categories.length);
        for (I18nConversionCategory category : categories) {
            names.add(category.name());
        }
        return names;
    }

    private static final class AnnotatedApiUsage {
        private final @NonNull String required;
        private @MonotonicNonNull String optional;
        private final @Untainted String cleanText;
        private final @Unsigned byte rawByte;
        private final @Present String present;
        private final @Interned String internedLabel;

        private AnnotatedApiUsage(@NonNull String required, @Nullable String optional, @Untainted String cleanText,
                @Unsigned byte rawByte) {
            this.required = NullnessUtil.castNonNull(required);
            this.optional = optional;
            this.cleanText = cleanText;
            this.rawByte = rawByte;
            this.present = Opt.get(required);
            this.internedLabel = "checker".intern();
        }

        private @NonNull String requiredValue() {
            return required;
        }

        private @NonNull String optionalValue() {
            return Opt.orElse(optional, "fallback");
        }

        private @Regex(1) String groupedPattern() {
            return RegexUtil.asRegex("(checker)", 1);
        }

        private @Format({ConversionCategory.GENERAL, ConversionCategory.INT}) String printfPattern() {
            return FormatUtil.asFormat("%s:%d", ConversionCategory.GENERAL, ConversionCategory.INT);
        }

        private @I18nFormat({I18nConversionCategory.NUMBER, I18nConversionCategory.DATE}) String messagePattern() {
            String pattern = "{0,number,integer} on {1,date,short}";
            assertThat(I18nFormatUtil.hasFormat(pattern, I18nConversionCategory.NUMBER, I18nConversionCategory.DATE))
                    .isTrue();
            return pattern;
        }

        private @Unsigned byte rawByteValue() {
            return rawByte;
        }

        private @Untainted String cleanValue() {
            assertThat(present).isEqualTo(required);
            assertThat(internedLabel).isSameAs("checker");
            return cleanText;
        }
    }
}
