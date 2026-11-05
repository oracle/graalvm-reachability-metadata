/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml.jaxrpc_api;

import java.net.URL;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceFactory;

public class ServiceFileServiceFactory extends ServiceFactory {
    public ServiceFileServiceFactory() {
    }

    @Override
    public Service createService(URL wsdlLocation, QName serviceName) {
        throw new UnsupportedOperationException("This discovery provider does not create services");
    }

    @Override
    public Service createService(QName serviceName) {
        throw new UnsupportedOperationException("This discovery provider does not create services");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Service loadService(Class serviceInterface) {
        throw new UnsupportedOperationException("This discovery provider does not load services");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Service loadService(URL wsdlLocation, Class serviceInterface, Properties properties) {
        throw new UnsupportedOperationException("This discovery provider does not load services");
    }

    @Override
    public Service loadService(URL wsdlLocation, QName serviceName, Properties properties) {
        throw new UnsupportedOperationException("This discovery provider does not load services");
    }
}
