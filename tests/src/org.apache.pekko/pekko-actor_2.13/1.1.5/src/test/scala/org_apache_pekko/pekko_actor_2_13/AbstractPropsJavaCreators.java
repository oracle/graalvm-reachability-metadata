/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.japi.Creator;

public final class AbstractPropsJavaCreators {
    public Creator<JavaConstructorProbeActor> newAnonymousInnerCreator() {
        return new Creator<JavaConstructorProbeActor>() {
            @Override
            public JavaConstructorProbeActor create() {
                return new JavaConstructorProbeActor();
            }
        };
    }

    public static final class JavaConstructorProbeActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }
    }
}
