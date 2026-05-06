/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_stream_2_13;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.stream.SystemMaterializer;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PekkoStreamActorBootstrapTest {
    @Test
    void startsActorSystemAndLoadsStreamMaterializer() throws Exception {
        ActorSystem system = ActorSystem.create("PekkoStreamBootstrap" + System.nanoTime());
        try {
            assertThat(SystemMaterializer.class).isNotNull();
            ActorRef actor = system.actorOf(Props.empty(), "empty");
            assertThat(actor.path().name()).isEqualTo("empty");
        } finally {
            Await.result(system.terminate(), Duration.create("10 seconds"));
        }
    }
}
