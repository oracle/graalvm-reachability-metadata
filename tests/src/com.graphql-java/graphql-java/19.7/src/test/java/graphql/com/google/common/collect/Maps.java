/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.LinkedHashMap;

public final class Maps {

  private Maps() {
  }

  public static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<>(expectedSize);
  }
}
