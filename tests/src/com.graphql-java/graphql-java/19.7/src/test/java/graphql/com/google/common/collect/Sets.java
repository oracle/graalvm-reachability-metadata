/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Sets {

  private Sets() {
  }

  public static <E> HashSet<E> newHashSetWithExpectedSize(int expectedSize) {
    return new HashSet<>(expectedSize);
  }

  public static <E> SetView<E> intersection(Set<E> first, Set<?> second) {
    Set<E> result = new LinkedHashSet<>();
    for (E element : first) {
      if (second.contains(element)) {
        result.add(element);
      }
    }
    return new SetView<>(result);
  }

  public static final class SetView<E> extends AbstractSet<E> {

    private final Set<E> delegate;

    private SetView(Set<E> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Iterator<E> iterator() {
      return delegate.iterator();
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
}
