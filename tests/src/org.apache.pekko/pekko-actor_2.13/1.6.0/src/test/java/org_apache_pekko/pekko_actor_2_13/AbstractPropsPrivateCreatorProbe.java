/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.Creator;

public final class AbstractPropsPrivateCreatorProbe {
    private AbstractPropsPrivateCreatorProbe() {
    }

    public static Class<? extends AbstractActor> actorClass() {
        return SilentActor.class;
    }

    public static Props createPropsWithPackagePrivateCreator() {
        return Props.create(SilentActor.class, new PackagePrivateCreator());
    }

    public static final class SilentActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return AbstractActor.emptyBehavior();
        }
    }

    static final class PackagePrivateCreator implements Creator<SilentActor> {
        @Override
        public SilentActor create() {
            return new SilentActor();
        }
    }
}
