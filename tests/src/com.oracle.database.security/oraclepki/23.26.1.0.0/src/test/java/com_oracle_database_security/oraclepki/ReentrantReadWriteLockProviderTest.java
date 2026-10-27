/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThatCode;

import oracle.security.pki.ReentrantReadWriteLockProvider;
import org.junit.jupiter.api.Test;

public class ReentrantReadWriteLockProviderTest {
    @Test
    void supportsNestedReadLockLifecycle() {
        String wallet = "wallet-lock-key";

        assertThatCode(
                        () -> {
                            ReentrantReadWriteLockProvider.lockForRead(wallet);
                            ReentrantReadWriteLockProvider.lockForRead(wallet);
                            ReentrantReadWriteLockProvider.unlockForRead(wallet);
                            ReentrantReadWriteLockProvider.unlockForRead(wallet);
                        })
                .doesNotThrowAnyException();
    }
}
