/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.fake;

import java.util.Set;
import java.util.function.Supplier;

import org.immutables.processor.ProxyProcessor;

public final class ProxyProcessorCaller implements Supplier<Set<String>> {

    @Override
    public Set<String> get() {
        return new ProxyProcessor().getSupportedAnnotationTypes();
    }
}
