/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import java.io.Serializable;
import java.security.PrivilegedAction;

final class LineNumberSerializableLambdas {
    private LineNumberSerializableLambdas() {
    }

    static PrivilegedAction<String> action(String value) {
        return (PrivilegedAction<String> & Serializable) () -> value;
    }
}
