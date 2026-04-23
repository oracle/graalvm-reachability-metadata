/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ConnectionTester;

import java.sql.Connection;

public class RegistryConnectionTester implements ConnectionTester {
    @Override
    public int activeCheckConnection(Connection connection) {
        return CONNECTION_IS_OKAY;
    }

    @Override
    public int statusOnException(Connection connection, Throwable throwable) {
        return CONNECTION_IS_INVALID;
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
