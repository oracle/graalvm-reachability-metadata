/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.japi.Creator;

final class AbstractPropsCreatorFactory {
    Creator<AbstractPropsJavaActor> nonStaticAnonymousCreator() {
        return new Creator<>() {
            @Override
            public AbstractPropsJavaActor create() {
                return new AbstractPropsJavaActor();
            }
        };
    }

    Class<AbstractPropsJavaActor> actorClass() {
        return AbstractPropsJavaActor.class;
    }
}

final class AbstractPropsJavaActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}
