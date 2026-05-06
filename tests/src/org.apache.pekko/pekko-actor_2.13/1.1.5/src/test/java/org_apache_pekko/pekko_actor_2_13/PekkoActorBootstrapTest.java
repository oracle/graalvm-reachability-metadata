/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PekkoActorBootstrapTest {
    @Test
    void startsActorSystemAndEmptyActorFromReferenceConfig() throws Exception {
        ActorSystem system = ActorSystem.create("PekkoActorBootstrap" + System.nanoTime());
        try {
            assertThat(system.settings().config().hasPath("pekko.actor.provider")).isTrue();
            ActorRef actor = system.actorOf(Props.empty(), "empty");
            assertThat(actor.path().name()).isEqualTo("empty");
        } finally {
            Await.result(system.terminate(), Duration.create("10 seconds"));
        }
    }
}
