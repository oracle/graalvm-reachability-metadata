/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13;

import java.io.Serializable;

public final class SerializableJavaLambdaFactory {
    private SerializableJavaLambdaFactory() {
    }

    public static SerializableCallable create(String value) {
        return () -> value;
    }

    @FunctionalInterface
    public interface SerializableCallable extends Serializable {
        String call();
    }
}
