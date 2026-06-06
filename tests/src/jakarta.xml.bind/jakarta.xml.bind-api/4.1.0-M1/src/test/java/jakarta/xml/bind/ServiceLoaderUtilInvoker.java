/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta.xml.bind;

import java.util.Map;

import jakarta_xml_bind.jakarta_xml_bind_api.support.FactoryBackedContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.LegacyContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.PropertiesContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.WrongTypeContextFactory;

public final class ServiceLoaderUtilInvoker {
    private static final ServiceLoaderUtil.ExceptionHandler<JAXBException> EXCEPTION_HANDLER =
            new ServiceLoaderUtil.ExceptionHandler<>() {
                @Override
                public JAXBException createException(Throwable throwable, String message) {
                    return new JAXBException(message, throwable);
                }
            };

    private ServiceLoaderUtilInvoker() {
    }

    public static FactoryBackedContextFactory instantiateFactoryBackedContextFactory() throws JAXBException {
        return (FactoryBackedContextFactory) ServiceLoaderUtil.newInstance(
                FactoryBackedContextFactory.class.getName(),
                FactoryBackedContextFactory.class.getName(),
                EXCEPTION_HANDLER);
    }

    public static Class<?> loadFactoryBackedContextFactory(ClassLoader classLoader) throws ClassNotFoundException {
        return ServiceLoaderUtil.safeLoadClass(
                FactoryBackedContextFactory.class.getName(),
                FactoryBackedContextFactory.class.getName(),
                classLoader);
    }

    public static JAXBContext createContextWithPropertiesFactory(
            String contextPath,
            ClassLoader classLoader,
            Map<String, ?> properties) throws JAXBException {
        return ContextFinder.newInstance(
                contextPath,
                ModuleUtil.getClassesFromContextPath(contextPath, classLoader),
                PropertiesContextFactory.class,
                classLoader,
                properties);
    }

    public static JAXBContext createContextWithPropertiesFactory(
            Class<?>[] classes,
            Map<String, ?> properties) throws JAXBException {
        return ContextFinder.newInstance(classes, properties, PropertiesContextFactory.class);
    }

    public static JAXBContext createContextWithLegacyFactory(
            String contextPath,
            ClassLoader classLoader,
            Map<String, ?> properties) throws JAXBException {
        return ContextFinder.newInstance(
                contextPath,
                ModuleUtil.getClassesFromContextPath(contextPath, classLoader),
                LegacyContextFactory.class,
                classLoader,
                properties);
    }

    public static JAXBContext createContextWithFactoryBackedFactory(
            Class<?>[] classes,
            Map<String, ?> properties) throws JAXBException {
        return ContextFinder.newInstance(classes, properties, FactoryBackedContextFactory.class);
    }

    public static JAXBContext createContextWithWrongTypeFactory(
            Class<?>[] classes,
            Map<String, ?> properties) throws JAXBException {
        return ContextFinder.newInstance(classes, properties, WrongTypeContextFactory.class);
    }
}
