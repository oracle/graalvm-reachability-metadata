/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import akka.japi.Creator;

public final class AbstractPropsCreatorFactory {
    private AbstractPropsCreatorFactory() {
    }

    public static Creator<AbstractPropsCreatorActor> anonymousCreator() {
        return new Creator<AbstractPropsCreatorActor>() {
            @Override
            public AbstractPropsCreatorActor create() {
                return new AbstractPropsCreatorActor("anonymous");
            }
        };
    }
}
