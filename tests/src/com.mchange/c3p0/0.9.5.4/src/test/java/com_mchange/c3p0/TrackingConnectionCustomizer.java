/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ConnectionCustomizer;

import java.sql.Connection;

public class TrackingConnectionCustomizer implements ConnectionCustomizer {
    @Override
    public void onAcquire(Connection c, String parentDataSourceIdentityToken) {
    }

    @Override
    public void onDestroy(Connection c, String parentDataSourceIdentityToken) {
    }

    @Override
    public void onCheckOut(Connection c, String parentDataSourceIdentityToken) {
    }

    @Override
    public void onCheckIn(Connection c, String parentDataSourceIdentityToken) {
    }
}
