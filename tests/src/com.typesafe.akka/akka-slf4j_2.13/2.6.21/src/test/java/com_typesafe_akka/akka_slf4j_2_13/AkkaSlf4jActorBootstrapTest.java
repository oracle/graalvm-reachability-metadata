/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_2_13;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.slf4j.Slf4jLogger;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AkkaSlf4jActorBootstrapTest {
    @Test
    void startsActorSystemAndLoadsSlf4jLogger() throws Exception {
        ActorSystem system = ActorSystem.create("AkkaSlf4jBootstrap" + System.nanoTime());
        try {
            assertThat(Slf4jLogger.class).isNotNull();
            ActorRef actor = system.actorOf(Props.empty(), "empty");
            assertThat(actor.path().name()).isEqualTo("empty");
        } finally {
            Await.result(system.terminate(), Duration.create("10 seconds"));
        }
    }
}
