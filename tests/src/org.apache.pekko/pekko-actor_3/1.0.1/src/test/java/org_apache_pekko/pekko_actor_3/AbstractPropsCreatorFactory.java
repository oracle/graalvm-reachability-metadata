/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.japi.Creator;

public final class AbstractPropsCreatorFactory {
    public Creator<AbstractPropsJavaActor> newEnclosedCreator() {
        return new Creator<AbstractPropsJavaActor>() {
            @Override
            public AbstractPropsJavaActor create() {
                return new AbstractPropsJavaActor();
            }
        };
    }

    public static final class AbstractPropsJavaActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }
    }
}
