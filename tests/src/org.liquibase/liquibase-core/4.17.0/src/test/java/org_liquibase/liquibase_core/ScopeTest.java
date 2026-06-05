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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ScopeTest {

    @Test
    void getSingletonCreatesScopeAwareSingletonFromRootScope() throws Exception {
        Scope rootScope = Scope.getCurrentScope();
        String rootLineSeparator = rootScope.getLineSeparator();

        ScopeAwareSingleton singleton = Scope.child(
                Map.of(Scope.Attr.lineSeparator.name(), "child-line-separator"),
                () -> Scope.getCurrentScope().getSingleton(ScopeAwareSingleton.class));

        assertThat(singleton.capturedScope()).isSameAs(rootScope);
        assertThat(singleton.capturedLineSeparator()).isEqualTo(rootLineSeparator);
        assertThat(Scope.getCurrentScope().getSingleton(ScopeAwareSingleton.class)).isSameAs(singleton);
    }

    public static final class ScopeAwareSingleton implements SingletonObject {
        private final Scope capturedScope;
        private final String capturedLineSeparator;

        private ScopeAwareSingleton(Scope scope) {
            this.capturedScope = scope;
            this.capturedLineSeparator = scope.getLineSeparator();
        }

        Scope capturedScope() {
            return capturedScope;
        }

        String capturedLineSeparator() {
            return capturedLineSeparator;
        }
    }
}
