/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.pekko.stream.javadsl.AsPublisher;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import play.api.libs.streams.Probes;
import scala.runtime.AbstractFunction1;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ProbesTest extends StreamTestSupport {
    @Test
    void flowProbeLogsAndPreservesStreamElements() throws Exception {
        List<String> logged = new CopyOnWriteArrayList<>();
        List<String> result = await(Source.from(List.of("alpha", "beta", "gamma"))
                .via(Probes.flowProbe("flow", new AbstractFunction1<String, String>() {
                    @Override
                    public String apply(String value) {
                        logged.add(value);
                        return "element=" + value;
                    }
                }).asJava()).runWith(Sink.seq(), materializer));

        assertEquals(List.of("alpha", "beta", "gamma"), result);
        assertEquals(result, logged);
    }

    @Test
    void publisherProbeForwardsSubscriptionDemandAndCompletion() throws Exception {
        List<String> logged = new CopyOnWriteArrayList<>();
        Publisher<String> publisher = Source.from(List.of("red", "blue"))
                .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);
        Publisher<String> probe = Probes.publisherProbe("publisher", publisher, new AbstractFunction1<String, String>() {
            @Override
            public String apply(String value) {
                logged.add(value);
                return value;
            }
        });

        List<String> result = await(Source.fromPublisher(probe).runWith(Sink.seq(), materializer));
        assertEquals(List.of("red", "blue"), result);
        assertEquals(result, logged);
    }
}
