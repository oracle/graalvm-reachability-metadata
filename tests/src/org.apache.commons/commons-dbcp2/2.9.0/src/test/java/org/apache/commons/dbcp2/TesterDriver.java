/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class TesterDriver implements Driver {
    private static final Properties validUserPasswords = new Properties();

    static {
        try {
            DriverManager.registerDriver(new TesterDriver());
        } catch (final Exception e) {
        }
        validUserPasswords.put("foo", "bar");
        validUserPasswords.put("u1", "p1");
        validUserPasswords.put("u2", "p2");
        validUserPasswords.put("userName", "password");
    }

    private static final String CONNECT_STRING = "jdbc:apache:commons:testdriver";

    private static final int MAJOR_VERSION = 1;

    private static final int MINOR_VERSION = 0;

    public static void addUser(final String userName, final String password) {
        synchronized (validUserPasswords) {
            validUserPasswords.put(userName, password);
        }
    }

    @Override
    public boolean acceptsURL(final String url) {
        return url != null && url.startsWith(CONNECT_STRING);
    }

    private void assertValidUserPassword(final String userName, final String password)
            throws SQLException {
        if (userName == null) {
            throw new SQLException("user name cannot be null.");
        }
        synchronized (validUserPasswords) {
            final String realPassword = validUserPasswords.getProperty(userName);
            if (realPassword == null) {
                throw new SQLException(userName + " is not a valid user name.");
            }
            if (!realPassword.equals(password)) {
                throw new SQLException(password + " is not the correct password for " + userName
                        + ".  The correct password is " + realPassword);
            }
        }
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        Connection conn = null;
        if (acceptsURL(url)) {
            String userName = "test";
            String password = "test";
            if (info != null) {
                userName = info.getProperty(Constants.KEY_USER);
                password = info.getProperty(Constants.KEY_PASSWORD);
                if (userName == null) {
                    final String[] parts = url.split(";");
                    userName = parts[1];
                    userName = userName.split("=")[1];
                    password = parts[2];
                    password = password.split("=")[1];
                }
                assertValidUserPassword(userName, password);
            }
            conn = new TesterConnection(userName, password);
        }

        return conn;
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

}
