/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.commonjs.module.ModuleScript;
import net.sourceforge.htmlunit.corejs.javascript.commonjs.module.provider.ModuleSource;
import net.sourceforge.htmlunit.corejs.javascript.commonjs.module.provider.ModuleSourceProvider;
import net.sourceforge.htmlunit.corejs.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SoftCachingModuleScriptProviderTest {
    @Test
    void serializationRoundTripReinitializesSoftCacheForModuleLoading() throws Exception {
        SoftCachingModuleScriptProvider provider =
                new SoftCachingModuleScriptProvider(new StaticModuleSourceProvider());

        SoftCachingModuleScriptProvider restored = roundTrip(provider);
        ModuleScript moduleScript = loadModule(restored, StaticModuleSourceProvider.MODULE_ID);

        assertThat(moduleScript.getUri()).isEqualTo(StaticModuleSourceProvider.MODULE_URI);
        assertThat(execute(moduleScript)).isEqualTo("module-loaded-after-serialization");
    }

    private static ModuleScript loadModule(
            SoftCachingModuleScriptProvider provider, String moduleId) throws Exception {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            return provider.getModuleScript(cx, moduleId, null, null, null);
        } finally {
            Context.exit();
        }
    }

    private static String execute(ModuleScript moduleScript) {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = moduleScript.getScript().exec(cx, scope);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    private static SoftCachingModuleScriptProvider roundTrip(
            SoftCachingModuleScriptProvider provider) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new PlainObjectReplacingOutputStream(bytes)) {
            output.writeObject(provider);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(SoftCachingModuleScriptProvider.class);
            return (SoftCachingModuleScriptProvider) value;
        }
    }

    private static final class PlainObjectReplacingOutputStream extends ObjectOutputStream {
        private PlainObjectReplacingOutputStream(ByteArrayOutputStream bytes) throws Exception {
            super(bytes);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object != null && Object.class.equals(object.getClass())) {
                return PlainObjectReplacement.INSTANCE;
            }
            return object;
        }
    }

    private enum PlainObjectReplacement {
        INSTANCE
    }

    private static final class StaticModuleSourceProvider
            implements ModuleSourceProvider, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String MODULE_ID = "soft-cache-round-trip";
        private static final URI MODULE_URI = URI.create("memory:/soft-cache-round-trip.js");
        private static final String VALIDATOR = "soft-cache-validator";
        private static final String SOURCE = "'module-loaded-after-serialization';";

        @Override
        public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator) {
            if (!MODULE_ID.equals(moduleId)) {
                return null;
            }
            if (VALIDATOR.equals(validator)) {
                return NOT_MODIFIED;
            }
            return moduleSource();
        }

        @Override
        public ModuleSource loadSource(URI uri, URI baseUri, Object validator) {
            if (!MODULE_URI.equals(uri)) {
                return null;
            }
            if (VALIDATOR.equals(validator)) {
                return NOT_MODIFIED;
            }
            return moduleSource();
        }

        private static ModuleSource moduleSource() {
            return new ModuleSource(
                    new StringReader(SOURCE), null, MODULE_URI, null, VALIDATOR);
        }
    }
}
