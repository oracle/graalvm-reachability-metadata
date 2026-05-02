/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * Minimal relocated Guava collection API required by graphql-java 19.7.
 */
public abstract class ImmutableCollection<E> extends AbstractCollection<E> implements Serializable {

  final Collection<E> delegate;

  ImmutableCollection(Collection<E> delegate) {
    this.delegate = delegate;
  }

  @Override
  public UnmodifiableIterator<E> iterator() {
    Iterator<E> iterator = delegate.iterator();
    return new UnmodifiableIterator<>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public E next() {
        return iterator.next();
      }
    };
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean contains(Object element) {
    return delegate.contains(element);
  }
}
