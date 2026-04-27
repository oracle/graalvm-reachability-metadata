/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.security.krb5;

public class KrbException extends Exception {
    private final int errorCode;

    public KrbException(int errorCode) {
        super("IBM test Kerberos error " + errorCode);
        this.errorCode = errorCode;
    }

    public int returnCode() {
        return this.errorCode;
    }
}
