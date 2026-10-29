/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import scala.jdk.javaapi.FutureConverters;

abstract class StreamTestSupport {
    protected ActorSystem system;
    protected Materializer materializer;

    @BeforeEach
    void startStreams() {
        system = ActorSystem.create("play-streams-test");
        materializer = SystemMaterializer.get(system).materializer();
    }

    @AfterEach
    void stopStreams() throws Exception {
        if (system != null) {
            system.terminate();
            await(FutureConverters.asJava(system.whenTerminated()));
        }
    }

    protected static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
