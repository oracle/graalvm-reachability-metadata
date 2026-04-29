/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta.xml.bind;

import jakarta_xml_bind.jakarta_xml_bind_api.support.FactoryBackedContextFactory;

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
}
