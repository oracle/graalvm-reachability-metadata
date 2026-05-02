/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Iterator;

/**
 * Minimal relocated Guava iterator API required by graphql-java 19.7.
 */
public abstract class UnmodifiableIterator<E> implements Iterator<E> {

  @Override
  public final void remove() {
    throw new UnsupportedOperationException();
  }
}
