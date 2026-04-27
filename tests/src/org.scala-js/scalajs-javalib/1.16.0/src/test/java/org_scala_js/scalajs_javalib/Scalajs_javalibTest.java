/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_js.scalajs_javalib;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class Scalajs_javalibTest {

    @Test
    void supportsDirectBuffersByteOrderAndViewBuffers() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0x01020304);
        bytes.putInt(-17);
        bytes.putDouble(3.5d);
        bytes.flip();

        ByteBuffer slice = bytes.slice().order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = slice.asIntBuffer();

        assertThat(slice.isDirect()).isTrue();
        assertThat(slice.remaining()).isEqualTo(16);
        assertThat(ints.get()).isEqualTo(0x01020304);
        assertThat(ints.get()).isEqualTo(-17);

        slice.position(Integer.BYTES * 2);
        assertThat(slice.getDouble()).isEqualTo(3.5d);

        bytes.putInt(0, 42);
        assertThat(slice.getInt(0)).isEqualTo(42);
    }

    @Test
    void roundTripsBinaryAndCharacterStreams() throws Exception {
        ByteArrayOutputStream binaryOutput = new ByteArrayOutputStream();
        try (DataOutputStream dataOutput = new DataOutputStream(binaryOutput)) {
            dataOutput.writeBoolean(true);
            dataOutput.writeInt(123456789);
            dataOutput.writeUTF("héllo scala.js");
        }

        try (DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(binaryOutput.toByteArray()))) {
            assertThat(dataInput.readBoolean()).isTrue();
            assertThat(dataInput.readInt()).isEqualTo(123456789);
            assertThat(dataInput.readUTF()).isEqualTo("héllo scala.js");
        }

        StringWriter textOutput = new StringWriter();
        try (PrintWriter writer = new PrintWriter(textOutput)) {
            writer.println("alpha");
            writer.printf(Locale.ROOT, "%s=%d", "beta", 2);
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(textOutput.toString()))) {
            assertThat(reader.readLine()).isEqualTo("alpha");
            assertThat(reader.readLine()).isEqualTo("beta=2");
            assertThat(reader.readLine()).isNull();
        }
    }

    @Test
    void performsBigIntegerAndBigDecimalArithmeticWithoutPrecisionLoss() {
        BigInteger largeValue = new BigInteger("123456789012345678901234567890");
        BigInteger modulus = BigInteger.valueOf(97);
        BigInteger powered = new BigInteger("ff", 16).pow(8);
        BigInteger[] quotientAndRemainder = powered.divideAndRemainder(modulus);

        assertThat(largeValue.gcd(BigInteger.valueOf(90))).isEqualTo(BigInteger.valueOf(90));
        assertThat(quotientAndRemainder[0].multiply(modulus).add(quotientAndRemainder[1])).isEqualTo(powered);

        BigDecimal decimal = new BigDecimal("12345.6789");

        assertThat(decimal.movePointLeft(2).setScale(4, RoundingMode.HALF_UP))
                .isEqualByComparingTo("123.4568");
        assertThat(decimal.divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP))
                .isEqualByComparingTo("4115.226300");
        assertThat(new BigDecimal("10.5000").stripTrailingZeros().toPlainString()).isEqualTo("10.5");
    }

    @Test
    void resolvesUrisAndEncodesFormParameters() {
        URI base = URI.create("https://example.com/a/b/");
        URI resolved = base.resolve("../c/%7Bvalue%7D?x=1").normalize();
        String original = "scala js + native/测试";
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);

        assertThat(resolved).hasToString("https://example.com/a/c/%7Bvalue%7D?x=1");
        assertThat(encoded).isEqualTo("scala+js+%2B+native%2F%E6%B5%8B%E8%AF%95");
        assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void maintainsCollectionOrderingAndUtilityBehaviors() {
        LinkedHashMap<String, Integer> counters = new LinkedHashMap<>();
        counters.merge("alpha", 1, Integer::sum);
        counters.merge("beta", 2, Integer::sum);
        counters.merge("alpha", 3, Integer::sum);

        ArrayDeque<String> queue = new ArrayDeque<>(counters.keySet());
        queue.addFirst("start");
        queue.addLast("end");

        String joined = queue.stream().collect(Collectors.joining(">"));
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("scala.js".getBytes(StandardCharsets.UTF_8));
        Optional<String> highestCounterKey = counters.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);

        assertThat(counters.keySet()).containsExactly("alpha", "beta");
        assertThat(counters.get("alpha")).isEqualTo(4);
        assertThat(joined).isEqualTo("start>alpha>beta>end");
        assertThat(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)).isEqualTo("scala.js");
        assertThat(highestCounterKey).hasValue("alpha");
    }

    @Test
    void supportsAtomicStateTransitionsAndCounters() {
        AtomicInteger retries = new AtomicInteger(2);
        AtomicReference<String> phase = new AtomicReference<>("created");
        AtomicReferenceArray<String> steps = new AtomicReferenceArray<>(new String[]{"parse", "emit", "done"});
        LongAdder completedTasks = new LongAdder();

        assertThat(retries.addAndGet(5)).isEqualTo(7);
        assertThat(retries.compareAndSet(7, 11)).isTrue();
        assertThat(phase.compareAndSet("created", "running")).isTrue();
        assertThat(phase.getAndSet("completed")).isEqualTo("running");
        assertThat(steps.compareAndSet(1, "emit", "verify")).isTrue();
        assertThat(steps.getAndSet(2, "publish")).isEqualTo("done");

        completedTasks.increment();
        completedTasks.add(2);
        completedTasks.decrement();

        assertThat(retries.get()).isEqualTo(11);
        assertThat(phase.get()).isEqualTo("completed");
        assertThat(steps.get(0)).isEqualTo("parse");
        assertThat(steps.get(1)).isEqualTo("verify");
        assertThat(steps.get(2)).isEqualTo("publish");
        assertThat(completedTasks.sumThenReset()).isEqualTo(2);
        assertThat(completedTasks.sum()).isZero();
    }

    @Test
    void supportsNavigableMapViewsAndSortedLookups() {
        TreeMap<Integer, String> releaseStages = new TreeMap<>();
        releaseStages.put(9, "resolve");
        releaseStages.put(13, "compile");
        releaseStages.put(18, "test");
        releaseStages.put(21, "publish");

        NavigableMap<Integer, String> afternoonStages = releaseStages.tailMap(13, true);

        assertThat(releaseStages.firstEntry().getKey()).isEqualTo(9);
        assertThat(releaseStages.firstEntry().getValue()).isEqualTo("resolve");
        assertThat(releaseStages.floorKey(17)).isEqualTo(13);
        assertThat(releaseStages.ceilingKey(17)).isEqualTo(18);
        assertThat(releaseStages.subMap(9, true, 21, false).values())
                .containsExactly("resolve", "compile", "test");
        assertThat(releaseStages.descendingKeySet()).containsExactly(21, 18, 13, 9);

        Map.Entry<Integer, String> removedStage = afternoonStages.pollFirstEntry();

        assertThat(removedStage.getKey()).isEqualTo(13);
        assertThat(removedStage.getValue()).isEqualTo("compile");
        assertThat(releaseStages.keySet()).containsExactly(9, 18, 21);
        assertThat(afternoonStages.values()).containsExactly("test", "publish");
    }

    @Test
    void matchesRegularExpressionsAndFormatsStackTraceElements() {
        Pattern pattern = Pattern.compile("(?<word>\\p{L}+)-(?<digits>\\d+)");
        Matcher matcher = pattern.matcher("scala-123 and js-456");

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group("word")).isEqualTo("scala");
        assertThat(matcher.group("digits")).isEqualTo("123");
        assertThat(pattern.matcher("scala-123 and js-456").replaceAll(result ->
                result.group("word").toUpperCase(Locale.ROOT) + ":" + result.group("digits")))
                .isEqualTo("SCALA:123 and JS:456");

        StackTraceElement frame = new StackTraceElement("org.scalajs.Sample", "render", "Sample.scala", 27);

        assertThat(frame.getClassName()).isEqualTo("org.scalajs.Sample");
        assertThat(frame.getMethodName()).isEqualTo("render");
        assertThat(frame.getFileName()).isEqualTo("Sample.scala");
        assertThat(frame.getLineNumber()).isEqualTo(27);
        assertThat(frame.toString()).isEqualTo("org.scalajs.Sample.render(Sample.scala:27)");
    }
}
