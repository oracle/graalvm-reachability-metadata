/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Iterator;

public final class UnmodifiableIterator<E> implements Iterator<E> {

  private final Iterator<? extends E> iterator;

  private UnmodifiableIterator(Iterator<? extends E> iterator) {
    this.iterator = iterator;
  }

  public static <E> UnmodifiableIterator<E> copyOf(Iterator<? extends E> iterator) {
    return new UnmodifiableIterator<>(iterator);
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public E next() {
    return iterator.next();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
