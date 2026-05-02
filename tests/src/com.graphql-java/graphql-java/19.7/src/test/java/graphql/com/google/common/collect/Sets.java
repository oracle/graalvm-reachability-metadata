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

/**
 * Minimal relocated Guava set helpers required by graphql-java 19.7.
 */
public final class Sets {

  private Sets() {
  }

  public static <E> HashSet<E> newHashSetWithExpectedSize(int expectedSize) {
    return new HashSet<>(expectedSize);
  }

  public static <E> SetView<E> intersection(Set<E> set1, Set<?> set2) {
    LinkedHashSet<E> values = new LinkedHashSet<>();
    for (E element : set1) {
      if (set2.contains(element)) {
        values.add(element);
      }
    }
    return new SetView<>(values);
  }

  public static final class SetView<E> extends AbstractSet<E> {

    private final Set<E> set;

    private SetView(Set<E> set) {
      this.set = set;
    }

    @Override
    public Iterator<E> iterator() {
      return set.iterator();
    }

    @Override
    public int size() {
      return set.size();
    }

    public ImmutableSet<E> immutableCopy() {
      return ImmutableSet.copyOf(set);
    }
  }
}
