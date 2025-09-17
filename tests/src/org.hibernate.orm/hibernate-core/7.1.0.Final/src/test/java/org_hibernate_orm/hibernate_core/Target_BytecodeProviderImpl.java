/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.lang.reflect.Method;
import java.util.Set;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import net.bytebuddy.ClassFileVersion;
import org.hibernate.HibernateException;
import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.bytecode.enhance.spi.Enhancer;
import org.hibernate.bytecode.spi.BasicProxyFactory;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.CompositeType;

@TargetClass(className = "org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl")
final class Target_BytecodeProviderImpl {

    @Substitute
    Target_BytecodeProviderImpl() {

    }

    @Substitute
    Target_BytecodeProviderImpl(ClassFileVersion targetCompatibleJVM) {

    }

    @Substitute
    public ProxyFactoryFactory getProxyFactoryFactory() {
        return new ProxyFactoryFactory() {
            @Override
            public ProxyFactory buildProxyFactory(SessionFactoryImplementor sessionFactory) {
                return new ProxyFactory() {
                    @Override
                    public void postInstantiate(
                        String entityName,
                        Class<?> persistentClass,
                        Set<Class<?>> interfaces,
                        Method getIdentifierMethod,
                        Method setIdentifierMethod,
                        CompositeType componentIdType) {
                    }

                    @Override
                    public HibernateProxy getProxy(Object id, SharedSessionContractImplementor session) throws HibernateException {
                        throw new HibernateException("Generation of HibernateProxy instances at runtime is not allowed when the configured BytecodeProvider is 'none'; your model requires a more advanced BytecodeProvider to be enabled.");
                    }
                };
            }

            @Override
            public BasicProxyFactory buildBasicProxyFactory(Class superClassOrInterface) {
                return new BasicProxyFactory() {
                    @Override
                    public Object getProxy() {
                        throw new HibernateException("NoneBasicProxyFactory is unable to generate a BasicProxy for type " + superClassOrInterface + ". Enable a different BytecodeProvider.");
                    }
                };
            }
        };
    }


    @Substitute
    public ReflectionOptimizer getReflectionOptimizer(
        Class clazz,
        String[] getterNames,
        String[] setterNames,
        Class[] types) {
        throw new HibernateException("Using the ReflectionOptimizer is not possible when the configured BytecodeProvider is 'none'. Use a different BytecodeProvider");
    }

    @Substitute
    public Enhancer getEnhancer(EnhancementContext enhancementContext) {
        return null;
    }
}
