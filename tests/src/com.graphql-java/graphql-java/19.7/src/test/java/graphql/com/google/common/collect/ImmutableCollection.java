/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.AbstractCollection;
import java.util.Collection;

public final class ImmutableCollection<E> extends AbstractCollection<E> {

  private final Collection<E> delegate;

  ImmutableCollection(Collection<E> delegate) {
    this.delegate = delegate;
  }

  @Override
  public UnmodifiableIterator<E> iterator() {
    return UnmodifiableIterator.copyOf(delegate.iterator());
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean contains(Object object) {
    return delegate.contains(object);
  }
}
