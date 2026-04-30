/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.security.krb5;

public class KrbException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int returnCode;

    public KrbException(int returnCode) {
        this.returnCode = returnCode;
    }

    public int returnCode() {
        return returnCode;
    }
}
