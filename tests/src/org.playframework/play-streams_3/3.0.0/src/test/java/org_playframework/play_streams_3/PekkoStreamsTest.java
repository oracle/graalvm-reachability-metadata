/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.Done;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import play.api.libs.streams.PekkoStreams;
import scala.concurrent.Future;
import scala.jdk.javaapi.FutureConverters;
import scala.runtime.AbstractFunction1;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class PekkoStreamsTest extends StreamTestSupport {
    @Test
    void passesElementsWhileIgnoringUpstreamCompletion() throws Exception {
        assertEquals(List.of(1, 2, 3), await(Source.from(List.of(1, 2, 3))
                .via(PekkoStreams.<Integer>ignoreAfterFinish().asJava()).take(3)
                .runWith(Sink.seq(), materializer)));
    }

    @Test
    void drainsUpstreamAfterDownstreamCancels() throws Exception {
        AtomicInteger received = new AtomicInteger();
        Pair<Future<Done>, CompletionStage<Integer>> result = Source.from(List.of(1, 2, 3, 4, 5))
                .map(value -> {
                    received.incrementAndGet();
                    return value;
                })
                .viaMat(PekkoStreams.<Integer>ignoreAfterCancellation().asJava(), Keep.right())
                .toMat(Sink.head(), Keep.both()).run(materializer);

        assertEquals(1, await(result.second()));
        assertEquals(Done.getInstance(), await(FutureConverters.asJava(result.first())));
        assertEquals(5, received.get());
    }

    @Test
    void routesSelectedElementsAroundTheTransformingFlow() throws Exception {
        AtomicInteger transformed = new AtomicInteger();
        Flow<Integer, String, ?> flow = PekkoStreams.bypassWith(new AbstractFunction1<Integer, Either<Integer, String>>() {
            @Override
            public Either<Integer, String> apply(Integer value) {
                return value < 0 ? new Right<>("bypassed") : new Left<>(value);
            }
        }).apply(Flow.of(Integer.class).map(value -> {
            transformed.incrementAndGet();
            return "value=" + value;
        }).asScala()).asJava();

        List<String> result = await(Source.from(List.of(2, -1, 4)).via(flow).runWith(Sink.seq(), materializer));
        assertEquals(List.of("bypassed", "value=2", "value=4"), result.stream().sorted().toList());
        assertEquals(2, transformed.get());
    }
}
