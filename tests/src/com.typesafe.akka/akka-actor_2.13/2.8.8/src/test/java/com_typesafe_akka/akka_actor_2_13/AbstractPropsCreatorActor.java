/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import akka.actor.AbstractActor;

public final class AbstractPropsCreatorActor extends AbstractActor {
    private final String prefix;

    public AbstractPropsCreatorActor(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Message.class, message -> {
                    message.recorder.set(prefix + "-created");
                    message.latch.countDown();
                })
                .build();
    }

    public static final class Message {
        private final AtomicReference<String> recorder;
        private final CountDownLatch latch;

        public Message(AtomicReference<String> recorder, CountDownLatch latch) {
            this.recorder = recorder;
            this.latch = latch;
        }
    }
}
