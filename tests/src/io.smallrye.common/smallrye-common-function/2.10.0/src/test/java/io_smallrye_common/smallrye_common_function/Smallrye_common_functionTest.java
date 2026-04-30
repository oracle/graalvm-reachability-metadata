/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.smallrye.common.function.ExceptionBiConsumer;
import io.smallrye.common.function.ExceptionBiFunction;
import io.smallrye.common.function.ExceptionBiPredicate;
import io.smallrye.common.function.ExceptionBinaryOperator;
import io.smallrye.common.function.ExceptionConsumer;
import io.smallrye.common.function.ExceptionFunction;
import io.smallrye.common.function.ExceptionIntFunction;
import io.smallrye.common.function.ExceptionLongFunction;
import io.smallrye.common.function.ExceptionObjIntConsumer;
import io.smallrye.common.function.ExceptionObjLongConsumer;
import io.smallrye.common.function.ExceptionPredicate;
import io.smallrye.common.function.ExceptionRunnable;
import io.smallrye.common.function.ExceptionSupplier;
import io.smallrye.common.function.ExceptionToIntBiFunction;
import io.smallrye.common.function.ExceptionToIntFunction;
import io.smallrye.common.function.ExceptionToLongBiFunction;
import io.smallrye.common.function.ExceptionToLongFunction;
import io.smallrye.common.function.ExceptionUnaryOperator;
import io.smallrye.common.function.Functions;
import org.junit.jupiter.api.Test;

public class Smallrye_common_functionTest {
    @Test
    void functionsComposeWithFunctionsConsumersPredicatesAndSuppliers() throws TestException {
        ExceptionFunction<String, String, TestException> trim = value -> value.trim();
        ExceptionFunction<String, Integer, TestException> length = String::length;
        ExceptionBiFunction<String, String, String, TestException> describe = (original, trimmed) -> original
                + "->" + trimmed;
        List<String> consumed = new ArrayList<>();

        assertThat(trim.andThen(length).apply("  alpha  ")).isEqualTo(5);
        assertThat(length.compose(trim).apply("  beta ")).isEqualTo(4);
        assertThat(trim.andThen(describe).apply("  gamma ")).isEqualTo("  gamma ->gamma");

        trim.andThen((ExceptionConsumer<String, TestException>) consumed::add).accept(" delta ");
        ExceptionBiConsumer<String, String, TestException> recordOriginalAndResult = (original, result) -> consumed.add(
                original + result);
        trim.andThen(recordOriginalAndResult).accept(" epsilon ");

        assertThat(consumed).containsExactly("delta", " epsilon epsilon");
        assertThat(trim.andThen((ExceptionPredicate<String, TestException>) "zeta"::equals).test(" zeta ")).isTrue();
        ExceptionBiPredicate<String, String, TestException> originalContainsResult = (original, result) -> original
                .contains(result);
        assertThat(trim.andThen(originalContainsResult).test(" eta ")).isTrue();
        assertThat(trim.compose((ExceptionSupplier<String, TestException>) () -> " theta ").get()).isEqualTo("theta");
    }

    @Test
    void biFunctionsComposeArgumentsAndContinueWithFunctionsOrConsumers() throws TestException {
        ExceptionBiFunction<String, String, String, TestException> join = (left, right) -> left + ":" + right;
        ExceptionFunction<String, Integer, TestException> length = String::length;
        ExceptionConsumer<String, TestException> rejectEmptyJoin = value -> {
            if (value.equals(":")) {
                throw new TestException("blank");
            }
        };
        AtomicInteger observedLength = new AtomicInteger();

        assertThat(join.andThen(length).apply("red", "blue")).isEqualTo(8);
        join.andThen(rejectEmptyJoin).accept("red", "blue");
        assertThat(join.compose(() -> "left", () -> "right").get()).isEqualTo("left:right");

        join.andThen((ExceptionConsumer<String, TestException>) value -> observedLength.set(value.length()))
                .accept("a", "bc");
        assertThat(observedLength).hasValue(4);
        assertThatThrownBy(() -> join.andThen(rejectEmptyJoin).accept("", ""))
                .isInstanceOf(TestException.class)
                .hasMessage("blank");
    }

    @Test
    void predicatesApplyBooleanCombinatorsWithShortCircuitingAndXor() throws TestException {
        AtomicInteger calls = new AtomicInteger();
        ExceptionPredicate<String, TestException> startsWithA = value -> {
            calls.incrementAndGet();
            return value.startsWith("a");
        };
        ExceptionPredicate<String, TestException> endsWithZ = value -> {
            calls.incrementAndGet();
            return value.endsWith("z");
        };

        assertThat(startsWithA.and(endsWithZ).test("az")).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(startsWithA.and(endsWithZ).test("bz")).isFalse();
        assertThat(calls).hasValue(3);
        assertThat(startsWithA.or(endsWithZ).test("alpha")).isTrue();
        assertThat(calls).hasValue(4);
        assertThat(startsWithA.xor(endsWithZ).test("alpha")).isTrue();
        assertThat(calls).hasValue(6);
        assertThat(startsWithA.not().test("beta")).isTrue();
        assertThat(startsWithA.with(endsWithZ).test("alpha", "jazz")).isTrue();
    }

    @Test
    void biPredicatesApplyBooleanCombinatorsToBothArguments() throws TestException {
        ExceptionBiPredicate<String, Integer, TestException> lengthAtLeast = (text, minimum) -> text.length()
                >= minimum;
        ExceptionBiPredicate<String, Integer, TestException> containsDigit = (text, ignored) -> text.chars()
                .anyMatch(Character::isDigit);

        assertThat(lengthAtLeast.and(containsDigit).test("a1b2", 4)).isTrue();
        assertThat(lengthAtLeast.and(containsDigit).test("abc", 4)).isFalse();
        assertThat(lengthAtLeast.or(containsDigit).test("a1", 5)).isTrue();
        assertThat(lengthAtLeast.xor(containsDigit).test("abcde", 3)).isTrue();
        assertThat(lengthAtLeast.xor(containsDigit).test("a1b2", 3)).isFalse();
        assertThat(lengthAtLeast.not().test("abc", 5)).isTrue();
    }

    @Test
    void consumersAndRunnablesComposeInDocumentedOrder() throws TestException {
        List<String> events = new ArrayList<>();
        ExceptionRunnable<TestException> firstRun = () -> events.add("run:first");
        ExceptionRunnable<TestException> secondRun = () -> events.add("run:second");
        ExceptionConsumer<String, TestException> firstConsumer = value -> events.add("first:" + value);
        ExceptionConsumer<String, TestException> secondConsumer = value -> events.add("second:" + value);
        ExceptionBiConsumer<String, Integer, TestException> firstBiConsumer = (text, number) -> events.add(
                text + number);
        ExceptionBiConsumer<String, Integer, TestException> secondBiConsumer = (text, number) -> events.add(
                text.toUpperCase() + number);

        firstRun.andThen(secondRun).run();
        firstRun.compose(secondRun).run();
        firstConsumer.andThen(secondConsumer).accept("value");
        firstConsumer.compose(secondConsumer).accept("again");
        firstConsumer.compose(() -> "supplied").run();
        firstBiConsumer.andThen(secondBiConsumer).accept("n", 7);
        firstBiConsumer.compose(() -> "s", () -> 9).run();

        assertThat(events).containsExactly(
                "run:first", "run:second", "run:second", "run:first",
                "first:value", "second:value", "first:again", "second:again",
                "first:supplied", "n7", "N7", "s9");
    }

    @Test
    void suppliersContinueAsRunnablesOrMappedSuppliers() throws TestException {
        List<String> consumed = new ArrayList<>();
        ExceptionSupplier<String, TestException> supplier = () -> "payload";
        ExceptionConsumer<String, TestException> consumer = consumed::add;
        ExceptionFunction<String, Integer, TestException> length = String::length;

        supplier.andThen(consumer).run();

        assertThat(supplier.andThen(length).get()).isEqualTo(7);
        assertThat(consumed).containsExactly("payload");
    }

    @Test
    void primitiveFunctionsComposeThroughIntAndLongSpecializations() throws TestException {
        ExceptionToIntFunction<String, TestException> toInt = String::length;
        ExceptionToLongFunction<String, TestException> toLong = value -> value.length() * 10L;
        ExceptionIntFunction<String, TestException> describeInt = value -> "int:" + value;
        ExceptionLongFunction<String, TestException> describeLong = value -> "long:" + value;
        ExceptionToIntBiFunction<String, String, TestException> sumLengths = (left, right) -> left.length()
                + right.length();
        ExceptionToLongBiFunction<String, String, TestException> multiplyLengths = (left, right) -> (long) left.length()
                * right.length();

        assertThat(toInt.andThen(describeInt).apply("abcd")).isEqualTo("int:4");
        assertThat(toInt.andThen(describeLong).apply("abc")).isEqualTo("long:3");
        ExceptionFunction<Integer, String, TestException> repeat = value -> "x".repeat(value);
        assertThat(toInt.compose(repeat).apply(5)).isEqualTo(5);
        assertThat(toLong.andThen(describeLong).apply("abc")).isEqualTo("long:30");
        assertThat(toLong.compose(repeat).apply(4)).isEqualTo(40L);
        assertThat(describeInt.compose(toInt).apply("hello")).isEqualTo("int:5");
        assertThat(describeLong.compose(toLong).apply("hi")).isEqualTo("long:20");
        assertThat(sumLengths.andThen(describeInt).apply("ab", "cde")).isEqualTo("int:5");
        assertThat(sumLengths.andThen(describeLong).apply("ab", "c")).isEqualTo("long:3");
        assertThat(multiplyLengths.andThen(describeLong).apply("ab", "cde")).isEqualTo("long:6");
    }

    @Test
    void primitiveResultFunctionsChainToGeneralFunctions() throws TestException {
        List<String> calls = new ArrayList<>();
        ExceptionIntFunction<String, TestException> classifyInt = value -> {
            calls.add("int:" + value);
            return value % 2 == 0 ? "even" : "odd";
        };
        ExceptionLongFunction<String, TestException> classifyLong = value -> {
            calls.add("long:" + value);
            return value > 100L ? "large" : "small";
        };
        ExceptionFunction<String, String, TestException> decorate = value -> {
            calls.add("decorate:" + value);
            return "[" + value + "]";
        };

        assertThat(classifyInt.andThen(decorate).apply(8)).isEqualTo("[even]");
        assertThat(classifyLong.andThen(decorate).apply(42L)).isEqualTo("[small]");

        assertThat(calls).containsExactly("int:8", "decorate:even", "long:42", "decorate:small");
    }

    @Test
    void objectPrimitiveConsumersComposeWithMatchingAndWidenedLongConsumers() throws TestException {
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        StringBuilder third = new StringBuilder();
        ExceptionObjIntConsumer<StringBuilder, TestException> appendInt = (builder, value) -> builder.append("i")
                .append(value);
        ExceptionObjIntConsumer<StringBuilder, TestException> appendAgain = (builder, value) -> builder.append("a")
                .append(value);
        ExceptionObjLongConsumer<StringBuilder, TestException> appendLong = (builder, value) -> builder.append("l")
                .append(value);

        appendInt.andThen(appendAgain).accept(first, 3);
        appendInt.compose(appendAgain).accept(second, 4);
        appendInt.andThen(appendLong).compose(appendLong).accept(third, 5);

        assertThat(first).hasToString("i3a3");
        assertThat(second).hasToString("a4i4");
        assertThat(third).hasToString("l5i5l5");
    }

    @Test
    void objectLongConsumersComposeWhilePreservingLongValues() throws TestException {
        List<String> events = new ArrayList<>();
        long firstLargeValue = 4_294_967_296L;
        long secondLargeValue = firstLargeValue + 1L;
        ExceptionObjLongConsumer<String, TestException> record = (label, value) -> events.add(
                label + ":record:" + value);
        ExceptionObjLongConsumer<String, TestException> verifyLargeValue = (label, value) -> {
            assertThat(value).isGreaterThan(Integer.MAX_VALUE);
            events.add(label + ":verified:" + (value - 1L));
        };

        record.andThen(verifyLargeValue).accept("first", firstLargeValue);
        verifyLargeValue.compose(record).accept("second", secondLargeValue);

        assertThat(events).containsExactly(
                "first:record:4294967296",
                "first:verified:4294967295",
                "second:record:4294967297",
                "second:verified:4294967296");
    }

    @Test
    void unaryAndBinaryOperatorsKeepOperatorTypes() throws TestException {
        ExceptionUnaryOperator<String, TestException> upper = String::toUpperCase;
        ExceptionUnaryOperator<String, TestException> bracket = value -> "[" + value + "]";
        ExceptionFunction<String, String, TestException> reverse = value -> new StringBuilder(value).reverse()
                .toString();
        ExceptionBinaryOperator<String, TestException> join = (left, right) -> left + right;

        assertThat(ExceptionUnaryOperator.<String, TestException>identity().apply("same")).isEqualTo("same");
        assertThat(ExceptionUnaryOperator.of(reverse).apply("abc")).isEqualTo("cba");
        assertThat(ExceptionUnaryOperator.of(upper)).isSameAs(upper);
        assertThat(upper.andThen(bracket).apply("smallrye")).isEqualTo("[SMALLRYE]");
        assertThat(bracket.compose(upper).apply("smallrye")).isEqualTo("[SMALLRYE]");
        assertThat(join.andThen(bracket).apply("small", "rye")).isEqualTo("[smallrye]");
        assertThatThrownBy(() -> ExceptionUnaryOperator.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void functionAdaptersInvokeStandardAndExceptionFunctionalInterfaces() throws TestException {
        AtomicInteger counter = new AtomicInteger();
        List<String> values = new ArrayList<>();

        Functions.runnableConsumer().accept(() -> counter.incrementAndGet());
        Functions.<TestException>exceptionRunnableConsumer().accept(() -> counter.addAndGet(2));
        Functions.runnableExceptionConsumer().accept(() -> counter.addAndGet(3));
        Functions.<String>consumerBiConsumer().accept(values::add, "consumer");
        Functions.<String, TestException>exceptionConsumerBiConsumer().accept(values::add, "exception-consumer");
        Functions.<String>consumerExceptionBiConsumer().accept(values::add, "runtime-consumer");

        assertThat(Functions.<String>supplierFunction().apply(() -> "supplied")).isEqualTo("supplied");
        assertThat(Functions.<String, TestException>exceptionSupplierFunction().apply(() -> "exception-supplied"))
                .isEqualTo("exception-supplied");
        assertThat(Functions.<String>supplierExceptionFunction().apply(() -> "runtime-supplied"))
                .isEqualTo("runtime-supplied");
        assertThat(Functions.<String>supplierFunctionBiFunction().apply(Supplier::get, () -> "bi-supplied"))
                .isEqualTo("bi-supplied");
        assertThat(Functions.<String, TestException>exceptionSupplierFunctionBiFunction()
                .apply(ExceptionSupplier::get, () -> "exception-bi-supplied"))
                .isEqualTo("exception-bi-supplied");
        assertThat(Functions.<String, Integer>functionBiFunction().apply(String::length, "four"))
                .isEqualTo(4);
        assertThat(Functions.<String, Integer, TestException>exceptionFunctionBiFunction()
                .apply(String::length, "five5"))
                .isEqualTo(5);
        assertThat(Functions.<String, Integer>functionExceptionBiFunction().apply(String::length, "sixsix"))
                .isEqualTo(6);
        assertThat(counter).hasValue(6);
        assertThat(values).containsExactly("consumer", "exception-consumer", "runtime-consumer");
    }

    @Test
    void constantCapturingDiscardingAndClosingHelpersHaveExpectedSideEffects() throws TestException {
        AtomicInteger counter = new AtomicInteger();
        StringBuilder builder = new StringBuilder();
        RecordingCloseable closeable = new RecordingCloseable();

        assertThat(Functions.constantSupplier("constant").get()).isEqualTo("constant");
        assertThat(Functions.constantSupplier(null).get()).isNull();
        assertThat(Functions.<String, TestException>constantExceptionSupplier("exception-constant").get())
                .isEqualTo("exception-constant");

        Functions.capturingRunnable((String value) -> builder.append(value), "one").run();
        Functions.capturingRunnable((String left, String right) -> builder.append(left).append(right), "t", "wo").run();
        Functions.exceptionCapturingRunnable((String value) -> builder.append(value), "three").run();
        Functions.exceptionCapturingRunnable((String left, String right) -> builder.append(left).append(right),
                "fo", "ur").run();
        Functions.<String>discardingConsumer().accept("ignored");
        Functions.<String, TestException>discardingExceptionConsumer().accept("ignored");
        Functions.<String, Integer>discardingBiConsumer().accept("ignored", 1);
        Functions.<String, Integer, TestException>discardingExceptionBiConsumer().accept("ignored", 2);
        Functions.<RecordingCloseable>closingConsumer().accept(closeable);

        assertThat(builder).hasToString("onetwothreefour");
        assertThat(counter).hasValue(0);
        assertThat(closeable.closed()).isTrue();
        assertThatCode(() -> Functions.exceptionLoggingConsumer().accept(new TestException("logged")))
                .doesNotThrowAnyException();
    }

    @Test
    void quietAndRuntimeExceptionHelpersBridgeCheckedExceptionsToJdkConsumers() {
        List<Exception> handled = new ArrayList<>();
        Consumer<String> quietConsumer = Functions.quiet(value -> {
            throw new TestException("consumer:" + value);
        }, handled::add);
        ExceptionBiConsumer<String, Integer, TestException> throwingBiConsumer = (value, number) -> {
            throw new TestException("bi:" + value + number);
        };
        ExceptionObjIntConsumer<String, TestException> throwingObjIntConsumer = (value, number) -> {
            throw new TestException("int:" + value + number);
        };
        ExceptionObjLongConsumer<String, TestException> throwingObjLongConsumer = (value, number) -> {
            throw new TestException("long:" + value + number);
        };
        BiConsumer<String, Integer> quietBiConsumer = Functions.quiet(throwingBiConsumer, handled::add);
        ObjIntConsumer<String> quietObjIntConsumer = Functions.quiet(throwingObjIntConsumer, handled::add);
        ObjLongConsumer<String> quietObjLongConsumer = Functions.quiet(throwingObjLongConsumer, handled::add);

        quietConsumer.accept("a");
        quietBiConsumer.accept("b", 2);
        quietObjIntConsumer.accept("c", 3);
        quietObjLongConsumer.accept("d", 4L);

        assertThat(handled).extracting(Throwable::getMessage)
                .containsExactly("consumer:a", "bi:b2", "int:c3", "long:d4");
        assertThatThrownBy(() -> Functions.runtimeExceptionThrowingConsumer(
                (TestException exception) -> new IllegalStateException("wrapped", exception))
                .accept(new TestException("checked")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("wrapped")
                .hasCauseInstanceOf(TestException.class);
    }

    @Test
    void castsKeepFunctionalBehaviorAcrossJdkAndExceptionFunctionalTypes() throws Exception {
        List<String> seen = new ArrayList<>();
        Consumer<Object> objectConsumer = value -> seen.add("consumer:" + value);
        Predicate<Object> objectPredicate = value -> value.toString().startsWith("s");
        Supplier<String> stringSupplier = () -> "supplier";
        Function<Object, String> objectFunction = Object::toString;
        DoubleFunction<String> doubleFunction = value -> "double:" + value;
        IntFunction<String> intFunction = value -> "int:" + value;
        LongFunction<String> longFunction = value -> "long:" + value;
        ToDoubleFunction<Object> toDoubleFunction = value -> value.toString().length() + 0.5d;
        ToIntFunction<Object> toIntFunction = value -> value.toString().length();
        ToLongFunction<Object> toLongFunction = value -> value.toString().length() * 10L;
        BiConsumer<Object, Number> biConsumer = (value, number) -> seen.add(value + ":" + number);
        ObjDoubleConsumer<Object> objDoubleConsumer = (value, number) -> seen.add(value + ":" + number);
        ObjIntConsumer<Object> objIntConsumer = (value, number) -> seen.add(value + ":" + number);
        ObjLongConsumer<Object> objLongConsumer = (value, number) -> seen.add(value + ":" + number);
        BiPredicate<Object, Number> biPredicate = (value, number) -> value.toString().length() == number.intValue();
        BiFunction<Object, Number, String> biFunction = (value, number) -> value + ":" + number;
        ToDoubleBiFunction<Object, Number> toDoubleBiFunction = (value, number) -> value.toString().length()
                + number.doubleValue();
        ToIntBiFunction<Object, Number> toIntBiFunction = (value, number) -> value.toString().length()
                + number.intValue();
        ToLongBiFunction<Object, Number> toLongBiFunction = (value, number) -> value.toString().length()
                + number.longValue();

        Consumer<String> castConsumer = Functions.cast(objectConsumer);
        Predicate<String> castPredicate = Functions.cast(objectPredicate);
        Supplier<Object> castSupplier = Functions.cast(stringSupplier);
        Function<String, Object> castFunction = Functions.cast(objectFunction);
        DoubleFunction<Object> castDoubleFunction = Functions.cast(doubleFunction);
        IntFunction<Object> castIntFunction = Functions.cast(intFunction);
        LongFunction<Object> castLongFunction = Functions.cast(longFunction);
        ToDoubleFunction<String> castToDoubleFunction = Functions.cast(toDoubleFunction);
        ToIntFunction<String> castToIntFunction = Functions.cast(toIntFunction);
        ToLongFunction<String> castToLongFunction = Functions.cast(toLongFunction);
        BiConsumer<String, Integer> castBiConsumer = Functions.cast(biConsumer);
        ObjDoubleConsumer<String> castObjDoubleConsumer = Functions.cast(objDoubleConsumer);
        ObjIntConsumer<String> castObjIntConsumer = Functions.cast(objIntConsumer);
        ObjLongConsumer<String> castObjLongConsumer = Functions.cast(objLongConsumer);
        BiPredicate<String, Integer> castBiPredicate = Functions.cast(biPredicate);
        BiFunction<String, Integer, Object> castBiFunction = Functions.cast(biFunction);
        ToDoubleBiFunction<String, Integer> castToDoubleBiFunction = Functions.cast(toDoubleBiFunction);
        ToIntBiFunction<String, Integer> castToIntBiFunction = Functions.cast(toIntBiFunction);
        ToLongBiFunction<String, Integer> castToLongBiFunction = Functions.cast(toLongBiFunction);

        castConsumer.accept("seen");
        castBiConsumer.accept("bi", 1);
        castObjDoubleConsumer.accept("double", 2.5d);
        castObjIntConsumer.accept("int", 3);
        castObjLongConsumer.accept("long", 4L);

        assertThat(castPredicate.test("smallrye")).isTrue();
        assertThat(castSupplier.get()).isEqualTo("supplier");
        assertThat(castFunction.apply("value")).isEqualTo("value");
        assertThat(castDoubleFunction.apply(1.5d)).isEqualTo("double:1.5");
        assertThat(castIntFunction.apply(7)).isEqualTo("int:7");
        assertThat(castLongFunction.apply(8L)).isEqualTo("long:8");
        assertThat(castToDoubleFunction.applyAsDouble("abcd")).isEqualTo(4.5d);
        assertThat(castToIntFunction.applyAsInt("abc")).isEqualTo(3);
        assertThat(castToLongFunction.applyAsLong("abc")).isEqualTo(30L);
        assertThat(castBiPredicate.test("abc", 3)).isTrue();
        assertThat(castBiFunction.apply("answer", 42)).isEqualTo("answer:42");
        assertThat(castToDoubleBiFunction.applyAsDouble("abc", 2)).isEqualTo(5.0d);
        assertThat(castToIntBiFunction.applyAsInt("abc", 2)).isEqualTo(5);
        assertThat(castToLongBiFunction.applyAsLong("abc", 2)).isEqualTo(5L);
        assertThat(seen).containsExactly("consumer:seen", "bi:1", "double:2.5", "int:3", "long:4");

        assertExceptionCasts();
    }

    private static void assertExceptionCasts() throws Exception {
        List<String> seen = new ArrayList<>();
        ExceptionConsumer<Object, TestException> consumer = value -> seen.add("consumer:" + value);
        ExceptionPredicate<Object, TestException> predicate = value -> value.toString().contains("yes");
        ExceptionSupplier<String, TestException> supplier = () -> "exception-supplier";
        ExceptionFunction<Object, String, TestException> function = Object::toString;
        ExceptionIntFunction<String, TestException> intFunction = value -> "int:" + value;
        ExceptionLongFunction<String, TestException> longFunction = value -> "long:" + value;
        ExceptionToIntFunction<Object, TestException> toIntFunction = value -> value.toString().length();
        ExceptionToLongFunction<Object, TestException> toLongFunction = value -> value.toString().length() * 2L;
        ExceptionBiConsumer<Object, Number, TestException> biConsumer = (value, number) -> seen.add(
                value + ":" + number);
        ExceptionObjIntConsumer<Object, TestException> objIntConsumer = (value, number) -> seen.add(
                value + ":" + number);
        ExceptionObjLongConsumer<Object, TestException> objLongConsumer = (value, number) -> seen.add(
                value + ":" + number);
        ExceptionBiPredicate<Object, Number, TestException> biPredicate = (value, number) -> value.toString().length()
                == number.intValue();
        ExceptionBiFunction<Object, Number, String, TestException> biFunction = (value, number) -> value + ":" + number;
        ExceptionToIntBiFunction<Object, Number, TestException> toIntBiFunction = (value, number) -> value.toString()
                .length() + number.intValue();
        ExceptionToLongBiFunction<Object, Number, TestException> toLongBiFunction = (value, number) -> value.toString()
                .length() + number.longValue();

        ExceptionConsumer<String, Exception> castConsumer = Functions.cast(consumer);
        ExceptionPredicate<String, Exception> castPredicate = Functions.cast(predicate);
        ExceptionSupplier<Object, Exception> castSupplier = Functions.cast(supplier);
        ExceptionFunction<String, Object, Exception> castFunction = Functions.cast(function);
        ExceptionIntFunction<Object, Exception> castIntFunction = Functions.cast(intFunction);
        ExceptionLongFunction<Object, Exception> castLongFunction = Functions.cast(longFunction);
        ExceptionToIntFunction<String, Exception> castToIntFunction = Functions.cast(toIntFunction);
        ExceptionToLongFunction<String, Exception> castToLongFunction = Functions.cast(toLongFunction);
        ExceptionBiConsumer<String, Integer, Exception> castBiConsumer = Functions.cast(biConsumer);
        ExceptionObjIntConsumer<String, Exception> castObjIntConsumer = Functions.cast(objIntConsumer);
        ExceptionObjLongConsumer<String, Exception> castObjLongConsumer = Functions.cast(objLongConsumer);
        ExceptionBiPredicate<String, Integer, Exception> castBiPredicate = Functions.cast(biPredicate);
        ExceptionBiFunction<String, Integer, Object, Exception> castBiFunction = Functions.cast(biFunction);
        ExceptionToIntBiFunction<String, Integer, Exception> castToIntBiFunction = Functions.cast(toIntBiFunction);
        ExceptionToLongBiFunction<String, Integer, Exception> castToLongBiFunction = Functions.cast(toLongBiFunction);

        castConsumer.accept("seen");
        castBiConsumer.accept("bi", 1);
        castObjIntConsumer.accept("int", 2);
        castObjLongConsumer.accept("long", 3L);

        assertThat(castPredicate.test("yes")).isTrue();
        assertThat(castSupplier.get()).isEqualTo("exception-supplier");
        assertThat(castFunction.apply("value")).isEqualTo("value");
        assertThat(castIntFunction.apply(4)).isEqualTo("int:4");
        assertThat(castLongFunction.apply(5L)).isEqualTo("long:5");
        assertThat(castToIntFunction.apply("abc")).isEqualTo(3);
        assertThat(castToLongFunction.apply("abc")).isEqualTo(6L);
        assertThat(castBiPredicate.test("abc", 3)).isTrue();
        assertThat(castBiFunction.apply("answer", 42)).isEqualTo("answer:42");
        assertThat(castToIntBiFunction.apply("abc", 2)).isEqualTo(5);
        assertThat(castToLongBiFunction.apply("abc", 2)).isEqualTo(5L);
        assertThat(seen).containsExactly("consumer:seen", "bi:1", "int:2", "long:3");
    }

    private static final class RecordingCloseable implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
        }

        private boolean closed() {
            return closed.get();
        }
    }

    private static final class TestException extends Exception {
        private TestException(String message) {
            super(message);
        }
    }
}
