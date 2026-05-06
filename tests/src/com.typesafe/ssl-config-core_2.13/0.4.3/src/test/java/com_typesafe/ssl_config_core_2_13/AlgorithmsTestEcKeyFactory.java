/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.ssl_config_core_2_13;

import java.security.Key;

public final class AlgorithmsTestEcKeyFactory {
    private AlgorithmsTestEcKeyFactory() {
    }

    public static Key toECKey(Key key) {
        return key;
    }
}
