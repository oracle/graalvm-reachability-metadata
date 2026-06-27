/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import akka.actor.Actor;
import akka.japi.Creator;

public final class AbstractPropsJavaCreatorFactory {
    public Creator<Actor> newNonStaticLocalCreator() {
        return new Creator<Actor>() {
            @Override
            public Actor create() {
                throw new AssertionError("creator should not be invoked during props validation");
            }
        };
    }
}
