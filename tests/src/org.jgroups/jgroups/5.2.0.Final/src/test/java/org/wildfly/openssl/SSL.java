/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.wildfly.openssl;

public final class SSL {
    private static int getInstanceCount;

    private SSL() {
    }

    public static SSL getInstance() {
        getInstanceCount++;
        return new SSL();
    }

    public static int getInstanceCount() {
        return getInstanceCount;
    }
}
