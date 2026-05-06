/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_pekko_http_server_3;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter;

import static org.assertj.core.api.Assertions.assertThat;

class TapirPekkoActorBootstrapTest {
    @Test
    void startsPekkoActorSystemFromTapirPekkoHttpClasspath() throws Exception {
        ActorSystem system = ActorSystem.create("TapirPekkoBootstrap" + System.nanoTime());
        try {
            assertThat(PekkoHttpServerInterpreter.class).isNotNull();
            ActorRef actor = system.actorOf(Props.empty(), "empty");
            assertThat(actor.path().name()).isEqualTo("empty");
        } finally {
            Await.result(system.terminate(), Duration.create("10 seconds"));
        }
    }
}
