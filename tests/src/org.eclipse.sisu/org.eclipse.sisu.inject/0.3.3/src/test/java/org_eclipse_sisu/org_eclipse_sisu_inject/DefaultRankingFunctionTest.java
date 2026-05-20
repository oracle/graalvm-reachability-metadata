/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Priority;

import org.eclipse.sisu.inject.DefaultRankingFunction;
import org.junit.jupiter.api.Test;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.UntargettedBinding;

public class DefaultRankingFunctionTest {
    @Test
    void rankUsesJsr250PriorityAnnotationWhenBindingImplementationIsKnown() {
        DefaultRankingFunction rankingFunction = new DefaultRankingFunction(13);
        Binding<Jsr250PriorityComponent> binding = new UntargettedTestBinding<>(Jsr250PriorityComponent.class);

        int rank = rankingFunction.rank(binding);

        assertThat(rank).isEqualTo(37);
        assertThat(rankingFunction.maxRank()).isEqualTo(13);
    }

    @Priority(37)
    private static final class Jsr250PriorityComponent {
    }

    private static final class UntargettedTestBinding<T> implements UntargettedBinding<T> {
        private final Key<T> key;

        private UntargettedTestBinding(Class<T> implementationType) {
            this.key = Key.get(implementationType);
        }

        @Override
        public Key<T> getKey() {
            return key;
        }

        @Override
        public Provider<T> getProvider() {
            throw new UnsupportedOperationException("Provider access is not required for ranking");
        }

        @Override
        public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <V> V acceptScopingVisitor(BindingScopingVisitor<V> visitor) {
            return visitor.visitNoScoping();
        }

        @Override
        public Object getSource() {
            return getClass();
        }

        @Override
        public <V> V acceptVisitor(ElementVisitor<V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public void applyTo(Binder binder) {
            throw new UnsupportedOperationException("Binding replay is not required for ranking");
        }
    }
}
