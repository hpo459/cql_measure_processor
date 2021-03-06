package org.opencds.cqf.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.server.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Meta;
import org.opencds.cqf.providers.MeasureResourceProvider;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by Chris Schuler on 12/11/2016.
 */
public class MeasureProcessingJpaServlet extends RestfulServer {

    private static final long serialVersionUID = 1L;

    private WebApplicationContext myAppCtx;

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {

        super.initialize();

        FhirVersionEnum fhirVersion = FhirVersionEnum.DSTU3;
        setFhirContext(new FhirContext(fhirVersion));

        // Get the spring context from the web container (it's declared in web.xml)
        myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        String resourceProviderBeanName = "myResourceProvidersDstu3";
        List<IResourceProvider> beans = myAppCtx.getBean(resourceProviderBeanName, List.class);
        setResourceProviders(beans);

        Object systemProvider = myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class);
        setPlainProviders(systemProvider);

        IFhirSystemDao<Bundle, Meta> systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
        JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao,
                myAppCtx.getBean(DaoConfig.class));
        confProvider.setImplementationDescription("Example Server");
        setServerConformanceProvider(confProvider);

        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);
        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

        /*
		 * Enable CORS
		 */
        CorsConfiguration config = new CorsConfiguration();
        CorsInterceptor corsInterceptor = new CorsInterceptor(config);
        config.addAllowedHeader("Origin");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("X-Requested-With");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Access-Control-Request-Method");
        config.addAllowedHeader("Access-Control-Request-Headers");
        config.addAllowedOrigin("*");
        config.addExposedHeader("Location");
        config.addExposedHeader("Content-Location");
        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));
        registerInterceptor(corsInterceptor);

        /*
		 * Load interceptors for the server from Spring (these are defined in FhirServerConfig.java)
		 */
        Collection<IServerInterceptor> interceptorBeans = myAppCtx.getBeansOfType(IServerInterceptor.class).values();
        for (IServerInterceptor interceptor : interceptorBeans) {
            this.registerInterceptor(interceptor);
        }

        // TODO: this is all very hacky
        MeasureResourceProvider provider = new MeasureResourceProvider(getResourceProviders());
        provider.setDao(((ca.uhn.fhir.jpa.rp.dstu3.MeasureResourceProvider) getResourceProviders().toArray()[61]).getDao());
        provider.setContext(((ca.uhn.fhir.jpa.rp.dstu3.MeasureResourceProvider) getResourceProviders().toArray()[61]).getContext());

        try {
            unregisterProvider(getResourceProviders().toArray()[61]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        registerProvider(provider);

        // Now register the logging interceptor
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        registerInterceptor(loggingInterceptor);

        // The SLF4j logger "test.accesslog" will receive the logging events
        loggingInterceptor.setLoggerName("logging.accesslog");

        // This is the format for each line. A number of substitution variables may
        // be used here. See the JavaDoc for LoggingInterceptor for information on
        // what is available.
        loggingInterceptor.setMessageFormat("Source[${remoteAddr}] Operation[${operationType} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}]");

        //setServerAddressStrategy(new HardcodedServerAddressStrategy("http://mydomain.com/fhir/baseDstu2"));
        //registerProvider(myAppCtx.getBean(TerminologyUploaderProviderDstu3.class));
    }
}
