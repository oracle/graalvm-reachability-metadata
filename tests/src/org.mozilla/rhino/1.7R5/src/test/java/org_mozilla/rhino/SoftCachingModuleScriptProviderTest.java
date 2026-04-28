/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

public class SoftCachingModuleScriptProviderTest {
    @Test
    void serializesProviderAndInitializesCacheAfterRestore() throws Exception {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            Scriptable scope = cx.initStandardObjects();
            StaticModuleSourceProvider sourceProvider = new StaticModuleSourceProvider(
                    "var restoredProviderValue = 40 + 2; restoredProviderValue;",
                    URI.create("memory:/modules/restored-provider.js"),
                    "restored-validator");
            SoftCachingModuleScriptProvider provider = new SoftCachingModuleScriptProvider(sourceProvider);

            byte[] serializedProvider = serialize(provider);
            SoftCachingModuleScriptProvider restoredProvider = deserialize(serializedProvider);

            ModuleScript restoredModule = restoredProvider.getModuleScript(cx, "restored-provider", null, null, null);
            Object result = restoredModule.getScript().exec(cx, scope);

            assertThat(Context.toNumber(result)).isEqualTo(42.0);
        } finally {
            Context.exit();
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new LockReplacingObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static SoftCachingModuleScriptProvider deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SoftCachingModuleScriptProvider) in.readObject();
        }
    }

    private static final class LockReplacingObjectOutputStream extends ObjectOutputStream {
        LockReplacingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object != null && object.getClass() == Object.class) {
                return "serializable-lock";
            }
            return super.replaceObject(object);
        }
    }

    private static final class StaticModuleSourceProvider implements ModuleSourceProvider, Serializable {
        private static final long serialVersionUID = 1L;

        private final String source;
        private final URI uri;
        private final String validator;

        StaticModuleSourceProvider(String source, URI uri, String validator) {
            this.source = source;
            this.uri = uri;
            this.validator = validator;
        }

        @Override
        public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator)
                throws IOException, URISyntaxException {
            return loadSource(validator);
        }

        @Override
        public ModuleSource loadSource(URI uri, URI baseUri, Object validator)
                throws IOException, URISyntaxException {
            return loadSource(validator);
        }

        private ModuleSource loadSource(Object requestedValidator) throws IOException {
            if (validator.equals(requestedValidator)) {
                return NOT_MODIFIED;
            }
            return new ModuleSource(new StringReader(source), null, uri, null, validator);
        }
    }
}
