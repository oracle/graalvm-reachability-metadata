/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.annotations_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Generated;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.EJBs;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContexts;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceProperty;
import javax.persistence.PersistenceUnit;
import javax.persistence.PersistenceUnits;
import javax.xml.ws.WebServiceRef;
import javax.xml.ws.WebServiceRefs;
import org.junit.jupiter.api.Test;

public class Annotations_apiTest {

    @Test
    void lifecycleAndResourceAnnotationsExposeDefaultsAndConfiguredValues() throws Exception {
        Resource defaultResource = field(FullyAnnotatedComponent.class, "defaultResource")
                .getAnnotation(Resource.class);
        Resource configuredResource = method(FullyAnnotatedComponent.class, "configure")
                .getAnnotation(Resource.class);
        Resources resources = FullyAnnotatedComponent.class.getAnnotationsByType(Resources.class)[0];

        assertThat(method(FullyAnnotatedComponent.class, "initialize")
                .isAnnotationPresent(PostConstruct.class)).isTrue();
        assertThat(method(FullyAnnotatedComponent.class, "shutdown").isAnnotationPresent(PreDestroy.class)).isTrue();

        assertThat(defaultResource.name()).isEmpty();
        assertThat(defaultResource.type()).isEqualTo(Object.class);
        assertThat(defaultResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(defaultResource.shareable()).isTrue();
        assertThat(defaultResource.description()).isEmpty();
        assertThat(defaultResource.mappedName()).isEmpty();

        assertThat(configuredResource.name()).isEqualTo("service/configuration");
        assertThat(configuredResource.type()).isEqualTo(Integer.class);
        assertThat(configuredResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(configuredResource.shareable()).isFalse();
        assertThat(configuredResource.description()).isEqualTo("Configuration service");
        assertThat(configuredResource.mappedName()).isEqualTo("mapped/configuration");

        assertThat(resources.value()).hasSize(2);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/orders");
        assertThat(resources.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resources.value()[0].description()).isEqualTo("Orders datasource");
        assertThat(resources.value()[1].name()).isEqualTo("mail/session");
        assertThat(resources.value()[1].authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
    }

    @Test
    void securityAnnotationsRetainRoleAndPolicyConfiguration() throws Exception {
        DeclareRoles declareRoles = SecuredComponent.class.getAnnotationsByType(DeclareRoles.class)[0];
        RunAs runAs = SecuredComponent.class.getAnnotationsByType(RunAs.class)[0];
        RolesAllowed rolesAllowed = method(SecuredComponent.class, "operatorOnly")
                .getAnnotation(RolesAllowed.class);

        assertThat(declareRoles.value()).containsExactly("admin", "operator", "auditor");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(SecuredComponent.class.getAnnotationsByType(DenyAll.class).length > 0).isTrue();
        assertThat(method(SecuredComponent.class, "openToAuthenticatedUsers").isAnnotationPresent(PermitAll.class))
                .isTrue();
        assertThat(rolesAllowed.value()).containsExactly("admin", "operator");
    }

    @Test
    void arrayValuedAnnotationMembersReturnDefensiveCopies() throws Exception {
        Resources resources = FullyAnnotatedComponent.class.getAnnotationsByType(Resources.class)[0];
        Resource originalFirstResource = resources.value()[0];
        Resource[] mutableResourceCopy = resources.value();
        mutableResourceCopy[0] = resources.value()[1];

        RolesAllowed rolesAllowed = method(SecuredComponent.class, "operatorOnly")
                .getAnnotation(RolesAllowed.class);
        String[] mutableRoleCopy = rolesAllowed.value();
        mutableRoleCopy[0] = "guest";

        assertThat(resources.value()).isNotSameAs(mutableResourceCopy);
        assertThat(resources.value()[0]).isEqualTo(originalFirstResource);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/orders");
        assertThat(rolesAllowed.value()).containsExactly("admin", "operator");
    }

    @Test
    void ejbAnnotationsExposeSingleAndContainerValues() throws Exception {
        EJB fieldEjb = field(EnterpriseReferences.class, "catalogBean").getAnnotation(EJB.class);
        EJB methodEjb = method(EnterpriseReferences.class, "setAuditBean", Runnable.class).getAnnotation(EJB.class);
        EJBs ejbs = EnterpriseReferences.class.getAnnotationsByType(EJBs.class)[0];

        assertThat(fieldEjb.name()).isEqualTo("ejb/catalog");
        assertThat(fieldEjb.description()).isEqualTo("Catalog service");
        assertThat(fieldEjb.beanInterface()).isEqualTo(CharSequence.class);
        assertThat(fieldEjb.beanName()).isEqualTo("CatalogBean");
        assertThat(fieldEjb.mappedName()).isEqualTo("mapped/catalog");

        assertThat(methodEjb.name()).isEqualTo("ejb/audit");
        assertThat(methodEjb.beanInterface()).isEqualTo(Runnable.class);
        assertThat(methodEjb.beanName()).isEqualTo("AuditBean");

        assertThat(ejbs.value()).hasSize(2);
        assertThat(ejbs.value()[0].name()).isEqualTo("ejb/inventory");
        assertThat(ejbs.value()[0].beanInterface()).isEqualTo(Comparable.class);
        assertThat(ejbs.value()[1].name()).isEqualTo("ejb/defaulted");
        assertThat(ejbs.value()[1].description()).isEmpty();
        assertThat(ejbs.value()[1].beanInterface()).isEqualTo(Object.class);
        assertThat(ejbs.value()[1].beanName()).isEmpty();
        assertThat(ejbs.value()[1].mappedName()).isEmpty();
    }

    @Test
    void persistenceAnnotationsExposeContextsUnitsPropertiesAndEnums() throws Exception {
        PersistenceContext context = field(PersistenceReferences.class, "entityManager")
                .getAnnotation(PersistenceContext.class);
        PersistenceUnit unit = method(PersistenceReferences.class, "setEntityManagerFactory", Object.class)
                .getAnnotation(PersistenceUnit.class);
        PersistenceContexts contexts = PersistenceReferences.class.getAnnotationsByType(PersistenceContexts.class)[0];
        PersistenceUnits units = MorePersistenceReferences.class.getAnnotationsByType(PersistenceUnits.class)[0];

        assertThat(context.name()).isEqualTo("persistence/orders");
        assertThat(context.unitName()).isEqualTo("ordersUnit");
        assertThat(context.type()).isEqualTo(PersistenceContextType.EXTENDED);
        assertThat(context.properties()).hasSize(2);
        assertThat(context.properties()[0].name()).isEqualTo("hibernate.show_sql");
        assertThat(context.properties()[0].value()).isEqualTo("true");
        assertThat(context.properties()[1].name()).isEqualTo("jakarta.persistence.lock.timeout");
        assertThat(context.properties()[1].value()).isEqualTo("5000");

        assertThat(unit.name()).isEqualTo("persistence/factory");
        assertThat(unit.unitName()).isEqualTo("factoryUnit");

        assertThat(contexts.value()).hasSize(2);
        assertThat(contexts.value()[0].name()).isEqualTo("persistence/defaultContext");
        assertThat(contexts.value()[0].type()).isEqualTo(PersistenceContextType.TRANSACTION);
        assertThat(contexts.value()[0].properties()).isEmpty();
        assertThat(contexts.value()[1].unitName()).isEqualTo("reportingUnit");

        assertThat(units.value()).hasSize(2);
        assertThat(units.value()[0].name()).isEqualTo("persistence/unitOne");
        assertThat(units.value()[0].unitName()).isEqualTo("unitOne");
        assertThat(units.value()[1].name()).isEqualTo("persistence/defaultUnit");
        assertThat(units.value()[1].unitName()).isEmpty();

        assertThat(PersistenceContextType.values())
                .containsExactly(PersistenceContextType.TRANSACTION, PersistenceContextType.EXTENDED);
        assertThat(PersistenceContextType.valueOf("EXTENDED")).isEqualTo(PersistenceContextType.EXTENDED);
    }

    @Test
    void webServiceAnnotationsExposeSingleAndContainerValues() throws Exception {
        WebServiceRef fieldRef = field(WebServiceReferences.class, "paymentPort")
                .getAnnotation(WebServiceRef.class);
        WebServiceRef methodRef = method(WebServiceReferences.class, "setNotificationPort", Object.class)
                .getAnnotation(WebServiceRef.class);
        WebServiceRefs refs = WebServiceReferences.class.getAnnotationsByType(WebServiceRefs.class)[0];

        assertThat(fieldRef.name()).isEqualTo("service/payment");
        assertThat(fieldRef.type()).isEqualTo(CharSequence.class);
        assertThat(fieldRef.value()).isEqualTo(Runnable.class);
        assertThat(fieldRef.wsdlLocation()).isEqualTo("META-INF/wsdl/payment.wsdl");
        assertThat(fieldRef.mappedName()).isEqualTo("mapped/payment");

        assertThat(methodRef.name()).isEqualTo("service/notification");
        assertThat(methodRef.type()).isEqualTo(Object.class);
        assertThat(methodRef.value()).isEqualTo(Object.class);
        assertThat(methodRef.wsdlLocation()).isEmpty();
        assertThat(methodRef.mappedName()).isEmpty();

        assertThat(refs.value()).hasSize(2);
        assertThat(refs.value()[0].name()).isEqualTo("service/inventory");
        assertThat(refs.value()[0].wsdlLocation()).isEqualTo("META-INF/wsdl/inventory.wsdl");
        assertThat(refs.value()[1].name()).isEqualTo("service/defaulted");
        assertThat(refs.value()[1].type()).isEqualTo(Object.class);
        assertThat(refs.value()[1].value()).isEqualTo(Object.class);
    }

    @Test
    void singleReferenceAnnotationsCanBeDeclaredOnTypes() {
        Resource resource = TypeLevelReferences.class.getAnnotationsByType(Resource.class)[0];
        EJB ejb = TypeLevelReferences.class.getAnnotationsByType(EJB.class)[0];
        PersistenceContext context = TypeLevelReferences.class.getAnnotationsByType(PersistenceContext.class)[0];
        PersistenceUnit unit = TypeLevelReferences.class.getAnnotationsByType(PersistenceUnit.class)[0];
        WebServiceRef serviceRef = TypeLevelReferences.class.getAnnotationsByType(WebServiceRef.class)[0];

        assertThat(resource.name()).isEqualTo("class/resource");
        assertThat(resource.type()).isEqualTo(Long.class);
        assertThat(ejb.name()).isEqualTo("class/ejb");
        assertThat(ejb.beanInterface()).isEqualTo(Runnable.class);
        assertThat(context.name()).isEqualTo("class/context");
        assertThat(context.unitName()).isEqualTo("classContextUnit");
        assertThat(unit.name()).isEqualTo("class/unit");
        assertThat(unit.unitName()).isEqualTo("classFactoryUnit");
        assertThat(serviceRef.name()).isEqualTo("class/service");
        assertThat(serviceRef.type()).isEqualTo(CharSequence.class);
    }

    @Test
    void annotationTypeContractsExposeRetentionAndTargets() {
        assertRuntimeAnnotation(Resource.class, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD);
        assertRuntimeAnnotation(Resources.class, ElementType.TYPE);
        assertRuntimeAnnotation(PostConstruct.class, ElementType.METHOD);
        assertRuntimeAnnotation(PreDestroy.class, ElementType.METHOD);
        assertRuntimeAnnotation(DeclareRoles.class, ElementType.TYPE);
        assertRuntimeAnnotation(DenyAll.class, ElementType.METHOD, ElementType.TYPE);
        assertRuntimeAnnotation(PermitAll.class, ElementType.TYPE, ElementType.METHOD);
        assertRuntimeAnnotation(RolesAllowed.class, ElementType.TYPE, ElementType.METHOD);
        assertRuntimeAnnotation(RunAs.class, ElementType.TYPE);
        assertRuntimeAnnotation(EJB.class, ElementType.METHOD, ElementType.TYPE, ElementType.FIELD);
        assertRuntimeAnnotation(EJBs.class, ElementType.TYPE);
        assertRuntimeAnnotation(PersistenceContext.class, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD);
        assertRuntimeAnnotation(PersistenceContexts.class, ElementType.TYPE);
        assertRuntimeAnnotation(PersistenceProperty.class);
        assertRuntimeAnnotation(PersistenceUnit.class, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD);
        assertRuntimeAnnotation(PersistenceUnits.class, ElementType.TYPE);
        assertRuntimeAnnotation(WebServiceRef.class, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD);
        assertRuntimeAnnotation(WebServiceRefs.class, ElementType.TYPE);

        assertThat(Generated.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.SOURCE);
        assertThat(Generated.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactlyInAnyOrder(
                        ElementType.ANNOTATION_TYPE,
                        ElementType.CONSTRUCTOR,
                        ElementType.FIELD,
                        ElementType.LOCAL_VARIABLE,
                        ElementType.METHOD,
                        ElementType.PACKAGE,
                        ElementType.PARAMETER,
                        ElementType.TYPE);
        assertThat(GeneratedType.class.getAnnotationsByType(Generated.class)).isEmpty();
    }

    @Test
    void enumTypesExposeStableNamesAndOrdering() {
        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("CONTAINER"))
                .isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(Resource.AuthenticationType.APPLICATION.name()).isEqualTo("APPLICATION");
        assertThat(Resource.AuthenticationType.CONTAINER.ordinal()).isZero();
    }

    private static void assertRuntimeAnnotation(Class<?> annotationType, ElementType... targets) {
        assertThat(annotationType.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotationType.getAnnotationsByType(Target.class)[0].value()).containsExactlyInAnyOrder(targets);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getDeclaredMethod(name, parameterTypes);
    }

    @Resources({
            @Resource(name = "jdbc/orders", type = CharSequence.class, description = "Orders datasource"),
            @Resource(name = "mail/session")
    })
    private static final class FullyAnnotatedComponent {

        @Resource
        private Object defaultResource;

        @PostConstruct
        void initialize() {
        }

        @PreDestroy
        void shutdown() {
        }

        @Resource(
                name = "service/configuration",
                type = Integer.class,
                authenticationType = Resource.AuthenticationType.APPLICATION,
                shareable = false,
                description = "Configuration service",
                mappedName = "mapped/configuration")
        Integer configure() {
            return 1;
        }
    }

    @DeclareRoles({"admin", "operator", "auditor"})
    @RunAs("system")
    @DenyAll
    private static final class SecuredComponent {

        @PermitAll
        void openToAuthenticatedUsers() {
        }

        @RolesAllowed({"admin", "operator"})
        void operatorOnly() {
        }
    }

    @EJBs({
            @EJB(name = "ejb/inventory", beanInterface = Comparable.class),
            @EJB(name = "ejb/defaulted")
    })
    private static final class EnterpriseReferences {

        @EJB(
                name = "ejb/catalog",
                description = "Catalog service",
                beanInterface = CharSequence.class,
                beanName = "CatalogBean",
                mappedName = "mapped/catalog")
        private Object catalogBean;

        @EJB(name = "ejb/audit", beanInterface = Runnable.class, beanName = "AuditBean")
        void setAuditBean(Runnable auditBean) {
        }
    }

    @PersistenceContexts({
            @PersistenceContext(name = "persistence/defaultContext"),
            @PersistenceContext(name = "persistence/reporting", unitName = "reportingUnit")
    })
    private static final class PersistenceReferences {

        @PersistenceContext(
                name = "persistence/orders",
                unitName = "ordersUnit",
                type = PersistenceContextType.EXTENDED,
                properties = {
                        @PersistenceProperty(name = "hibernate.show_sql", value = "true"),
                        @PersistenceProperty(name = "jakarta.persistence.lock.timeout", value = "5000")
                })
        private Object entityManager;

        @PersistenceUnit(name = "persistence/factory", unitName = "factoryUnit")
        void setEntityManagerFactory(Object entityManagerFactory) {
        }
    }

    @PersistenceUnits({
            @PersistenceUnit(name = "persistence/unitOne", unitName = "unitOne"),
            @PersistenceUnit(name = "persistence/defaultUnit")
    })
    private static final class MorePersistenceReferences {
    }

    @Resource(name = "class/resource", type = Long.class)
    @EJB(name = "class/ejb", beanInterface = Runnable.class)
    @PersistenceContext(name = "class/context", unitName = "classContextUnit")
    @PersistenceUnit(name = "class/unit", unitName = "classFactoryUnit")
    @WebServiceRef(name = "class/service", type = CharSequence.class)
    private static final class TypeLevelReferences {
    }

    @WebServiceRefs({
            @WebServiceRef(name = "service/inventory", wsdlLocation = "META-INF/wsdl/inventory.wsdl"),
            @WebServiceRef(name = "service/defaulted")
    })
    private static final class WebServiceReferences {

        @WebServiceRef(
                name = "service/payment",
                type = CharSequence.class,
                value = Runnable.class,
                wsdlLocation = "META-INF/wsdl/payment.wsdl",
                mappedName = "mapped/payment")
        private Object paymentPort;

        @WebServiceRef(name = "service/notification")
        void setNotificationPort(Object notificationPort) {
        }
    }

    @Generated(value = {"metadata-forge", "annotations-api"}, date = "2026-04-16", comments = "source only")
    private static final class GeneratedType {
    }
}
