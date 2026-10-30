/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.util.List;
import java.util.Locale;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import play.libs.streams.ActorFlow;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ActorFlowTest extends StreamTestSupport {
    @Test
    void exchangesMessagesThroughActorWithDefaultBuffer() throws Exception {
        Flow<String, String, ?> flow = ActorFlow.actorRef(
                output -> Props.create(UppercaseActor.class, () -> new UppercaseActor(output)), system, materializer);

        assertEquals(List.of("HELLO", "STREAMS"), await(Source.from(List.of("hello", "streams"))
                .via(flow).runWith(Sink.seq(), materializer)));
    }

    @Test
    void exchangesMessagesThroughActorWithExplicitBuffer() throws Exception {
        Flow<String, String, ?> flow = ActorFlow.actorRef(
                output -> Props.create(UppercaseActor.class, () -> new UppercaseActor(output)),
                32, OverflowStrategy.fail(), system, materializer);

        assertEquals(List.of("ONE", "TWO", "THREE"), await(Source.from(List.of("one", "two", "three"))
                .via(flow).runWith(Sink.seq(), materializer)));
    }

    public static class UppercaseActor extends AbstractActor {
        private final ActorRef output;

        public UppercaseActor(ActorRef output) {
            this.output = output;
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(String.class,
                    message -> output.tell(message.toUpperCase(Locale.ROOT), getSelf())).build();
        }

        @Override
        public void postStop() {
            output.tell(new Status.Success(NotUsed.getInstance()), getSelf());
        }
    }
}
