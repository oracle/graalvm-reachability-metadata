/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Minimal relocated Guava list API required by graphql-java 19.7.
 */
public final class ImmutableList<E> extends ImmutableCollection<E> implements List<E>, RandomAccess {

  private final List<E> list;

  private ImmutableList(List<E> list) {
    super(list);
    this.list = list;
  }

  public static <E> ImmutableList<E> of() {
    return new ImmutableList<>(List.of());
  }

  public static <E> ImmutableList<E> of(E element) {
    return copyOf(List.of(element));
  }

  public static <E> ImmutableList<E> of(E first, E second) {
    return copyOf(List.of(first, second));
  }

  public static <E> ImmutableList<E> of(E first, E second, E third) {
    return copyOf(List.of(first, second, third));
  }

  public static <E> ImmutableList<E> of(E first, E second, E third, E fourth) {
    return copyOf(List.of(first, second, third, fourth));
  }

  public static <E> ImmutableList<E> of(E first, E second, E third, E fourth, E fifth) {
    return copyOf(List.of(first, second, third, fourth, fifth));
  }

  @SafeVarargs
  public static <E> ImmutableList<E> of(E first, E second, E third, E fourth, E fifth, E sixth, E... rest) {
    ArrayList<E> values = new ArrayList<>(rest.length + 6);
    Collections.addAll(values, first, second, third, fourth, fifth, sixth);
    Collections.addAll(values, rest);
    return copyOf(values);
  }

  public static <E> ImmutableList<E> copyOf(java.util.Collection<? extends E> elements) {
    return new ImmutableList<>(Collections.unmodifiableList(new ArrayList<>(elements)));
  }

  public static <E> ImmutableList<E> copyOf(Iterable<? extends E> elements) {
    ArrayList<E> values = new ArrayList<>();
    for (E element : elements) {
      values.add(element);
    }
    return new ImmutableList<>(Collections.unmodifiableList(values));
  }

  public static <E> ImmutableList<E> copyOf(Iterator<? extends E> elements) {
    ArrayList<E> values = new ArrayList<>();
    elements.forEachRemaining(values::add);
    return new ImmutableList<>(Collections.unmodifiableList(values));
  }

  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public static <E> Builder<E> builderWithExpectedSize(int expectedSize) {
    return new Builder<>(expectedSize);
  }

  public static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }

  @Override
  public E get(int index) {
    return list.get(index);
  }

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public int indexOf(Object element) {
    return list.indexOf(element);
  }

  @Override
  public int lastIndexOf(Object element) {
    return list.lastIndexOf(element);
  }

  @Override
  public ListIterator<E> listIterator() {
    return list.listIterator();
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    return list.listIterator(index);
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return list.subList(fromIndex, toIndex);
  }

  @Override
  public Object[] toArray() {
    return list.toArray();
  }

  @Override
  public <T> T[] toArray(T[] array) {
    return list.toArray(array);
  }

  @Override
  public Spliterator<E> spliterator() {
    return list.spliterator();
  }

  @Override
  public boolean add(E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(int index, E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(java.util.Collection<? extends E> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(int index, java.util.Collection<? extends E> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public E remove(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(java.util.Collection<?> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(java.util.Collection<?> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void replaceAll(UnaryOperator<E> operator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public E set(int index, E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sort(java.util.Comparator<? super E> comparator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  public static final class Builder<E> {

    private final ArrayList<E> values;

    public Builder() {
      this.values = new ArrayList<>();
    }

    private Builder(int expectedSize) {
      this.values = new ArrayList<>(expectedSize);
    }

    public Builder<E> add(E element) {
      values.add(element);
      return this;
    }

    @SafeVarargs
    public final Builder<E> add(E... elements) {
      Collections.addAll(values, elements);
      return this;
    }

    public Builder<E> addAll(Iterable<? extends E> elements) {
      for (E element : elements) {
        values.add(element);
      }
      return this;
    }

    public Builder<E> addAll(Iterator<? extends E> elements) {
      elements.forEachRemaining(values::add);
      return this;
    }

    public ImmutableList<E> build() {
      return ImmutableList.copyOf(values);
    }
  }
}
