/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.transaction.TransactionManager;

import com.atomikos.icatch.jta.UserTransactionManager;
import net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DefaultTransactionManagerLookupInnerClassSelectorTest {
    @Test
    void loadsAtomikosTransactionManagerWhenEarlierSelectorsMiss() {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader parentClassLoader = previousClassLoader == null
                ? ClassLoader.getSystemClassLoader() : previousClassLoader;
        Thread.currentThread().setContextClassLoader(new NonBitronixClassLoader(parentClassLoader));
        try {
            DefaultTransactionManagerLookup lookup = new DefaultTransactionManagerLookup();
            TransactionManager transactionManager = lookup.getTransactionManager();

            assertThat(transactionManager).isInstanceOf(UserTransactionManager.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static final class NonBitronixClassLoader extends ClassLoader {
        private NonBitronixClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("bitronix.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
