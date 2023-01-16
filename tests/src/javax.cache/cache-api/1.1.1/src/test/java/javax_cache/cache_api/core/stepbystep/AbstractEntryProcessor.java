/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.core.stepbystep;

import javax.cache.processor.EntryProcessor;
import java.io.Serializable;

public abstract class AbstractEntryProcessor<K, V, T> implements EntryProcessor<K, V, T>, Serializable {
}
