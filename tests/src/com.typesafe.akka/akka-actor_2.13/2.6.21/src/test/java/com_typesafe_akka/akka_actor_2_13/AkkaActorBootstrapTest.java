/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AkkaActorBootstrapTest {
    @Test
    void startsActorSystemAndEmptyActorFromReferenceConfig() throws Exception {
        ActorSystem system = ActorSystem.create("AkkaActorBootstrap" + System.nanoTime());
        try {
            assertThat(system.settings().config().hasPath("akka.actor.provider")).isTrue();
            ActorRef actor = system.actorOf(Props.empty(), "empty");
            assertThat(actor.path().name()).isEqualTo("empty");
        } finally {
            Await.result(system.terminate(), Duration.create("10 seconds"));
        }
    }
}
