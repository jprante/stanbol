package org.apache.stanbol.cmsadapter.web.resources;

import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.stanbol.cmsadapter.core.mapping.MappingConfigurationImpl;
import org.apache.stanbol.cmsadapter.servicesapi.helper.OntologyResourceHelper;
import org.apache.stanbol.cmsadapter.servicesapi.mapping.MappingConfiguration;
import org.apache.stanbol.cmsadapter.servicesapi.mapping.MappingEngine;
import org.apache.stanbol.cmsadapter.servicesapi.model.web.CMSObject;
import org.apache.stanbol.cmsadapter.servicesapi.model.web.CMSObjects;
import org.apache.stanbol.cmsadapter.servicesapi.model.web.decorated.AdapterMode;
import org.apache.stanbol.cmsadapter.web.utils.RestURIHelper;
import org.apache.stanbol.commons.web.base.ContextHelper;
import org.apache.stanbol.commons.web.base.resource.BaseStanbolResource;
import org.apache.stanbol.ontologymanager.store.rest.client.RestClient;
import org.apache.stanbol.ontologymanager.store.rest.client.RestClientException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntModel;

@Path("/cmsadapter/{ontologyURI:.+}/notify")
public class NotifyResource extends BaseStanbolResource {
    private static final Logger logger = LoggerFactory.getLogger(NotifyResource.class);

    private MappingEngine engine;
    private RestClient storeClient;

    public NotifyResource(@Context ServletContext context) {
        this.storeClient = ContextHelper.getServiceFromContext(RestClient.class, context);
        try {
            BundleContext bundleContext = (BundleContext) context.getAttribute(BundleContext.class.getName());
            ServiceReference serviceReference = bundleContext.getServiceReferences(null,
                "(component.factory=org.apache.stanbol.cmsadapter.servicesapi.mapping.MappingEngineFactory)")[0];
            ComponentFactory componentFactory = (ComponentFactory) bundleContext.getService(serviceReference);
            ComponentInstance componentInstance = componentFactory
                    .newInstance(new Hashtable<Object,Object>());
            this.engine = (MappingEngine) componentInstance.getInstance();

        } catch (InvalidSyntaxException e) {
            logger.warn("Mapping engine instance could not be instantiated", e);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    @POST
    public Response notifyCreate(@PathParam("ontologyURI") String ontologyURI,
                                 @FormParam("createdObjects") CMSObjects cmsObjects,
                                 @QueryParam("adapterMode") AdapterMode adapterMode,
                                 @DefaultValue("true") @QueryParam("considerBridges") boolean considerBridges) {

        List<CMSObject> createdObjectList = cmsObjects.getClassificationObjectOrContentObject();
        OntModel model;
        try {
            model = OntologyResourceHelper.createOntModel(storeClient, ontologyURI,
                RestURIHelper.getOntologyHref(ontologyURI));
            MappingConfiguration conf = new MappingConfigurationImpl();
            conf.setOntModel(model);
            conf.setOntologyURI(ontologyURI);
            conf.setObjects((List<Object>) (List<?>) createdObjectList);
            conf.setAdapterMode(adapterMode);
            if (considerBridges) {
                conf.setBridgeDefinitions(OntologyResourceHelper.getBridgeDefinitions(model));
            }
            engine.createModel(conf);
            return Response.ok().build();

        } catch (UnsupportedEncodingException e) {
            logger.warn("Ontology content could not be transformed to bytes", e);
        } catch (RestClientException e) {
            logger.warn("Error occured while interacting with store", e);
            logger.warn("Message: " + e.getMessage());
        }
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * Specified {@link CMSObject}s to this resource will be updated by executing previously defined bridges.
     * Bridge definitions are obtained from the ontology model that specified with <i>ontologyURI</i>
     * 
     * @param ontologyURI
     * @param cmsObjects
     * @return
     */
    @SuppressWarnings("unchecked")
    @PUT
    public Response notifyUpdate(@PathParam("ontologyURI") String ontologyURI,
                                 @FormParam("updatedObjects") CMSObjects cmsObjects,
                                 @QueryParam("adapterMode") AdapterMode adapterMode,
                                 @QueryParam("considerBridges") @DefaultValue("true") Boolean considerBridges) {
        List<CMSObject> updatedObjectList = cmsObjects.getClassificationObjectOrContentObject();
        OntModel model;
        try {
            model = OntologyResourceHelper.getOntModel(storeClient, ontologyURI,
                RestURIHelper.getOntologyHref(ontologyURI));
            MappingConfiguration conf = new MappingConfigurationImpl();
            conf.setOntModel(model);
            conf.setOntologyURI(ontologyURI);
            conf.setObjects((List<Object>) (List<?>) updatedObjectList);
            conf.setAdapterMode(adapterMode);
            if (considerBridges) {
                conf.setBridgeDefinitions(OntologyResourceHelper.getBridgeDefinitions(model));
            }
            engine.updateModel(conf);
            return Response.ok().build();

        } catch (UnsupportedEncodingException e) {
            logger.warn("Ontology content could not be transformed to bytes", e);
        } catch (RestClientException e) {
            logger.warn("Error occured while interacting with store", e);
            logger.warn("Message: " + e.getMessage());
        }
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * Specified {@link CMSObject}s to this resource will be deleted from the generated ontology.
     * 
     * @param ontologyURI
     * @param cmsObjects
     * @return
     */
    @SuppressWarnings("unchecked")
    @DELETE
    public Response notifyDelete(@PathParam("ontologyURI") String ontologyURI,
                                 @FormParam("deletedObjects") CMSObjects cmsObjects,
                                 @QueryParam("considerBridges") @DefaultValue("true") Boolean considerBridges) {
        List<CMSObject> deletedObjectList = cmsObjects.getClassificationObjectOrContentObject();
        OntModel model;
        try {
            model = OntologyResourceHelper.getOntModel(storeClient, ontologyURI,
                RestURIHelper.getOntologyHref(ontologyURI));
            MappingConfiguration conf = new MappingConfigurationImpl();
            conf.setOntModel(model);
            conf.setOntologyURI(ontologyURI);
            conf.setObjects((List<Object>) (List<?>) deletedObjectList);
            conf.setAdapterMode(AdapterMode.STRICT_OFFLINE);
            if (considerBridges) {
                conf.setBridgeDefinitions(OntologyResourceHelper.getBridgeDefinitions(model));
            }
            engine.deleteModel(conf);
            return Response.ok().build();

        } catch (UnsupportedEncodingException e) {
            logger.warn("Ontology content could not be transformed to bytes", e);
        } catch (RestClientException e) {
            logger.warn("Error occured while interacting with store", e);
            logger.warn("Message: " + e.getMessage());
        }
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
}
