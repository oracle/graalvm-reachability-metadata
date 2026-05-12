/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.util.introspection.SecureIntrospectorImpl;
import org.junit.jupiter.api.Test;

public class SecureIntrospectorImplTest {
    @Test
    void permitsCommonValueTypesBeforeCheckingRestrictedClassLists() {
        SecureIntrospectorImpl introspector = new SecureIntrospectorImpl(
                new String[] {Integer.class.getName()},
                new String[] {Integer.class.getPackage().getName()},
                new Log(new NullLogSystem()));

        assertThat(introspector.checkObjectExecutePermission(Integer.class, "toString")).isTrue();
    }
}
