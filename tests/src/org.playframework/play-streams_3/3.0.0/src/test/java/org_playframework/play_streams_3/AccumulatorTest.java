/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import play.libs.streams.Accumulator;
import scala.jdk.javaapi.FutureConverters;
import scala.runtime.AbstractFunction1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class AccumulatorTest extends StreamTestSupport {
    private final Executor directExecutor = Runnable::run;

    @Test
    void transformsInputAndMapsAccumulatedResult() throws Exception {
        Accumulator<String, String> accumulator = Accumulator.fromSink(Sink.<Integer, Integer>fold(0, Integer::sum))
                .through(Flow.of(String.class).map(Integer::parseInt))
                .map(total -> "total=" + total, directExecutor)
                .mapFuture(value -> CompletableFuture.completedFuture(value + "!"), directExecutor);

        assertEquals("total=12!", await(accumulator.run(Source.from(List.of("3", "4", "5")), materializer)));
    }

    @Test
    void runsSinkAccumulatorWithEmptyOrSingleInput() throws Exception {
        Accumulator<String, List<String>> accumulator = Accumulator.fromSink(Sink.seq());

        assertEquals(List.of(), await(accumulator.run(materializer)));
        assertEquals(List.of("single"), await(accumulator.run("single", materializer)));
        assertEquals(List.of("sink"), await(Source.single("sink").runWith(accumulator.toSink(), materializer)));
    }

    @Test
    void recoversFailedInputStream() throws Exception {
        IllegalArgumentException failure = new IllegalArgumentException("input unavailable");
        Accumulator<Integer, Integer> accumulator = Accumulator.fromSink(Sink.<Integer, Integer>fold(0, Integer::sum))
                .recover(error -> {
                    assertSame(failure, error);
                    return 42;
                }, directExecutor);

        assertEquals(42, await(accumulator.run(Source.failed(failure), materializer)));
    }

    @Test
    void recoversFailedInputStreamAsynchronously() throws Exception {
        IllegalStateException failure = new IllegalStateException("stream unavailable");
        Accumulator<Integer, Integer> accumulator = Accumulator.fromSink(Sink.<Integer, Integer>fold(0, Integer::sum))
                .recoverWith(error -> {
                    assertSame(failure, error);
                    return CompletableFuture.completedFuture(17);
                }, directExecutor);

        assertEquals(17, await(accumulator.run(Source.failed(failure), materializer)));
    }

    @Test
    void doneAccumulatorMapsAnAlreadyAvailableResult() throws Exception {
        Accumulator<String, String> accumulator = Accumulator.<String, Integer>done(6)
                .map(value -> value * 2, directExecutor)
                .mapFuture(value -> CompletableFuture.completedFuture("answer=" + value), directExecutor);

        assertEquals("answer=12", await(accumulator.run(materializer)));
        assertEquals("answer=12", await(accumulator.run("ignored", materializer)));
        assertEquals("answer=12", await(accumulator.run(Source.from(List.of("ignored", "too")), materializer)));
    }

    @Test
    void doneAccumulatorWaitsForItsCompletionStage() throws Exception {
        CompletableFuture<String> pending = new CompletableFuture<>();
        Accumulator<String, String> accumulator = Accumulator.done(pending);
        CompletableFuture<String> result = accumulator.run(materializer).toCompletableFuture();
        assertFalse(result.isDone());
        pending.complete("ready");

        assertEquals("ready", await(result));
    }

    @Test
    void recoversFailedDoneAccumulator() throws Exception {
        IllegalArgumentException failure = new IllegalArgumentException("not ready");
        Accumulator<String, String> accumulator = Accumulator.done(CompletableFuture.<String>failedFuture(failure));

        assertEquals("fallback", await(accumulator.recover(error -> "fallback", directExecutor).run(materializer)));
        assertEquals("async fallback", await(accumulator.recoverWith(error -> {
            assertSame(failure, error);
            return CompletableFuture.completedFuture("async fallback");
        }, directExecutor).run(materializer)));
    }

    @Test
    void strictAccumulatorHandlesEmptySingleAndStreamInput() throws Exception {
        Accumulator<String, String> accumulator = Accumulator.strict(
                element -> CompletableFuture.completedFuture(element.orElse("empty")),
                Sink.<String, String>fold("", String::concat));

        assertEquals("empty", await(accumulator.run(materializer)));
        assertEquals("single", await(accumulator.run("single", materializer)));
        assertEquals("onetwo", await(accumulator.run(Source.from(List.of("one", "two")), materializer)));
        assertEquals("EMPTY", await(accumulator.map(value -> value.toUpperCase(Locale.ROOT), directExecutor).run(materializer)));
        assertEquals("7", await(accumulator.through(Flow.of(Integer.class).map(Object::toString)).run(7, materializer)));
    }

    @Test
    void forwardsEveryElementThroughItsProducedSource() throws Exception {
        Source<String, ?> forwarded = await(Accumulator.<String>source()
                .run(Source.from(List.of("north", "south", "east")), materializer));

        assertEquals(List.of("north", "south", "east"), await(forwarded.runWith(Sink.seq(), materializer)));
    }

    @Test
    void flattensAnAccumulatorSuppliedAfterTheStreamStarts() throws Exception {
        CompletableFuture<Accumulator<Integer, Integer>> pending = new CompletableFuture<>();
        Accumulator<Integer, Integer> flattened = Accumulator.flatten(pending, materializer);
        CompletableFuture<Integer> result = flattened.run(Source.from(List.of(2, 4, 6)), materializer).toCompletableFuture();
        pending.complete(Accumulator.fromSink(Sink.<Integer, Integer>fold(0, Integer::sum)));

        assertEquals(12, await(result));
    }

    @Test
    void flattenedAccumulatorPropagatesFailedCompletionStage() {
        IllegalStateException failure = new IllegalStateException("accumulator unavailable");
        Accumulator<Integer, Integer> flattened = Accumulator.flatten(CompletableFuture.failedFuture(failure), materializer);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> await(flattened.run(Source.single(1), materializer)));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void convertsSinkAndStrictAccumulatorsBetweenJavaAndScala() throws Exception {
        Accumulator<String, List<String>> sink = Accumulator.fromSink(Sink.seq());
        assertEquals(List.of("first", "second"), await(sink.asScala().asJava()
                .run(Source.from(List.of("first", "second")), materializer)));

        assertEquals("READY", await(FutureConverters.asJava(Accumulator.<String, String>done("ready")
                .asScala().map(new AbstractFunction1<String, String>() {
                    @Override
                    public String apply(String value) {
                        return value.toUpperCase(Locale.ROOT);
                    }
                }, system.dispatcher()).run(materializer))));
        assertEquals("strict", await(Accumulator.<String, String>strict(
                element -> CompletableFuture.completedFuture(element.orElse("empty")),
                Sink.<String, String>fold("", String::concat)).asScala().asJava().run("strict", materializer)));
    }
}
