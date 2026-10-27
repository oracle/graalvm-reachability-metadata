/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3;

import org.apache.pekko.actor.UntypedAbstractActor;
import org.apache.pekko.japi.Creator;

public final class AbstractPropsJavaHelper {
    private AbstractPropsJavaHelper() {
    }

    public static Creator<JavaActor> staticAnonymousCreator() {
        return new Creator<JavaActor>() {
            @Override
            public JavaActor create() {
                return new JavaActor();
            }
        };
    }

    public static final class JavaActor extends UntypedAbstractActor {
        @Override
        public void onReceive(Object message) {
        }
    }
}
