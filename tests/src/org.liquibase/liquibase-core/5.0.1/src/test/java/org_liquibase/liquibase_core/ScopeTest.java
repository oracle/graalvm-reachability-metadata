/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.SingletonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScopeTest {

    @Test
    void getSingletonCreatesClassWithScopeConstructor() throws Exception {
        Scope rootScope = Scope.getCurrentScope();

        ScopeAwareSingleton singleton = rootScope.getSingleton(ScopeAwareSingleton.class);

        assertThat(singleton.getScope()).isSameAs(rootScope);
        assertThat(rootScope.getSingleton(ScopeAwareSingleton.class)).isSameAs(singleton);

        Scope.child("scope-test-value", "child", () -> {
            Scope childScope = Scope.getCurrentScope();

            assertThat(childScope).isNotSameAs(rootScope);
            assertThat(childScope.getSingleton(ScopeAwareSingleton.class)).isSameAs(singleton);
        });
    }

    public static final class ScopeAwareSingleton implements SingletonObject {
        private final Scope scope;

        public ScopeAwareSingleton(Scope scope) {
            this.scope = scope;
        }

        Scope getScope() {
            return scope;
        }
    }
}
