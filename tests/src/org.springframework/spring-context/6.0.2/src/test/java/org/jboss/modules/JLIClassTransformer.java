/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.modules;

import org.jboss.as.server.deployment.module.DelegatingClassFileTransformer;

public final class JLIClassTransformer {

    @SuppressWarnings("unused")
    private final DelegatingClassFileTransformer transformer;

    public JLIClassTransformer(DelegatingClassFileTransformer transformer) {
        this.transformer = transformer;
    }

    public DelegatingClassFileTransformer getTransformer() {
        return this.transformer;
    }
}
