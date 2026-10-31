/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.as.server.deployment.module;

import java.lang.instrument.ClassFileTransformer;
import java.util.ArrayList;
import java.util.List;

public final class DelegatingClassFileTransformer {

    private final List<ClassFileTransformer> transformers = new ArrayList<>();

    public void addTransformer(ClassFileTransformer transformer) {
        this.transformers.add(transformer);
    }

    public List<ClassFileTransformer> getTransformers() {
        return this.transformers;
    }
}
