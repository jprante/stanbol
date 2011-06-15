/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.entityhub.core.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.entityhub.core.query.QueryResultListImpl;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.query.FieldQuery;
import org.apache.stanbol.entityhub.servicesapi.query.QueryResultList;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSite;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSiteException;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSiteManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Component(immediate = true)
@Service
@Properties(value={
})
public class ReferenceManagerImpl implements ReferencedSiteManager {

    private final Logger log;

//    private ComponentContext context;
    public ReferenceManagerImpl(){
        super();
        log = LoggerFactory.getLogger(ReferenceManagerImpl.class);
        log.info(" create instance of "+ReferenceManagerImpl.class);
    }
    @Reference(
            cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE,
            referenceInterface=ReferencedSite.class,
            strategy=ReferenceStrategy.EVENT,
            policy=ReferencePolicy.DYNAMIC,
            bind="bindReferencedSites",
            unbind="unbindReferencedSites")
    List<ReferencedSite> referencedSites = new CopyOnWriteArrayList<ReferencedSite>();
    /**
     * Map holding the mapping of the site ID to the referencedSite Object
     * TODO: in principle it could be possible that two instances of
     * {@link ReferencedSite} could be configured to use the same ID
     */
    private final Map<String,ReferencedSite> idMap =
        Collections.synchronizedMap(new HashMap<String,ReferencedSite>());
    /**
     * Map holding the mappings between entityPrefixes and referenced sites
     */
    private final Map<String,Collection<ReferencedSite>> prefixMap =
        Collections.synchronizedMap(new TreeMap<String, Collection<ReferencedSite>>());
    /**
     * This List is used for binary searches within the prefixes to find the
     * {@link ReferencedSite} to search for a {@link #getEntity(String)}
     * request.<b>
     * NOTE: Every access to this list MUST BE synchronised to {@link #prefixMap}
     * TODO: I am quite sure, that there is some ioUtils class that provides
     * both a Map and an sorted List over the keys!
     */
    private final List<String> prefixList = new ArrayList<String>();
    private final Set<ReferencedSite> noPrefixSites = Collections.synchronizedSet(
        new HashSet<ReferencedSite>());

    @Activate
    protected void activate(ComponentContext context) {
        log.debug("Activate ReferenceManager");
    }
    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.debug("Deactivate ReferenceManager");
        synchronized (prefixMap) {
            this.prefixList.clear();
            this.prefixMap.clear();
            this.noPrefixSites.clear();
        }
        this.idMap.clear();
    }
    @Override
    public void addReferredSite(String baseUri, Dictionary<String,?> properties) {
        //TODO: implement
        throw new UnsupportedOperationException();

    }

    protected void bindReferencedSites(ReferencedSite referencedSite){
        log.debug(" ... binding ReferencedSite {}",referencedSite.getId());
        referencedSites.add(referencedSite);
        idMap.put(referencedSite.getId(), referencedSite);
        addEntityPrefixes(referencedSite);
    }
    protected void unbindReferencedSites(ReferencedSite referencedSite){
        log.debug(" ... unbinding ReferencedSite {}",referencedSite.getId());
        referencedSites.remove(referencedSite);
        idMap.remove(referencedSite.getId());
        removeEntityPrefixes(referencedSite);
    }
    /**
     * Adds the prefixes of the parsed Site to the Map holding the according mappings
     * @param referencedSite
     */
    private void addEntityPrefixes(ReferencedSite referencedSite) {
        String[] prefixArray = referencedSite.getConfiguration().getEntityPrefixes();
        if(prefixArray == null || prefixArray.length < 1){
            synchronized (prefixMap) {
                noPrefixSites.add(referencedSite);
            }
        } else {
            //use a set to iterate to remove possible duplicates
            for(String prefix : new HashSet<String>(Arrays.asList(prefixArray))){
                synchronized (prefixMap) {
                    Collection<ReferencedSite> sites = prefixMap.get(prefix);
                    if(sites == null){
                        sites = new CopyOnWriteArrayList<ReferencedSite>();
                        prefixMap.put(prefix, sites);
                        //this also means that the prefix is not part of the prefixList
                        int pos = Collections.binarySearch(prefixList, prefix);
                        if(pos<0){
                            prefixList.add(Math.abs(pos)-1,prefix);
                        }
                        //prefixList.add(Collections.binarySearch(prefixList, prefix)+1,prefix);
                    }
                    //TODO: Sort the referencedSites based on the ServiceRanking!
                    sites.add(referencedSite);
                }
            }
        }
    }
    /**
     * Removes the prefixes of the parsed Site to the Map holding the according mappings
     * @param referencedSite
     */
    private void removeEntityPrefixes(ReferencedSite referencedSite) {
        String[] prefixes = referencedSite.getConfiguration().getEntityPrefixes();
        if(prefixes == null || prefixes.length < 1){
            synchronized (prefixMap) {
                noPrefixSites.remove(referencedSite);
            }
        } else {
            for(String prefix : prefixes){
                synchronized (prefixMap) {
                    Collection<ReferencedSite> sites = prefixMap.get(prefix);
                    if(sites != null){
                        sites.remove(referencedSite);
                        if(sites.isEmpty()){
                            //remove key from the Map
                            prefixMap.remove(prefix);
                            //remove also the prefix from the List
                            prefixList.remove(prefix);
                        }
                    }
                }
            }
        }
    }
    @Override
    public ReferencedSite getReferencedSite(String id) {
        return idMap.get(id);
    }
    @Override
    public Collection<String> getReferencedSiteIds() {
        return Collections.unmodifiableCollection(idMap.keySet());
    }

    @Override
    public boolean isReferred(String id) {
        return idMap.containsKey(id);
    }
    @Override
    public Collection<ReferencedSite> getReferencedSitesByEntityPrefix(String entityUri) {
        if(entityUri == null){
            log.warn("NULL value parsed for Parameter entityUri -> return emptyList!");
            return Collections.emptyList();
        }
        synchronized (prefixMap) { //sync is done via the Map (for both the list and the map)!
            int pos = Collections.binarySearch(prefixList, entityUri);
            final int prefixPos;
            if(pos < 0){
                /**
                 * Example:
                 * ["a","b"] <- "bc"
                 * binary search returns -3 (because insert point would be +2)
                 * to find the prefix we need the insert point-1 -> pos 1
                 *
                 * Example2:
                 * [] <- "bc"
                 * binary search returns -1 (because insert point would be 0)
                 * to find the prefix we need the insert point-1 -> pos -1
                 * therefore we need to check for negative prefixPos and return
                 * an empty list!
                 */
                prefixPos = Math.abs(pos)-2;
            } else {
                prefixPos = pos; //entityUri found in list
            }
            if(prefixPos<0){
                return Collections.unmodifiableCollection(noPrefixSites);
            } else {
                String prefix = prefixList.get(prefixPos);
                if(entityUri.startsWith(prefix)){
                    log.debug("Found prefix {} for Entity {}",prefix,entityUri);
                    Collection<ReferencedSite> prefixSites = prefixMap.get(prefix);
                    Collection<ReferencedSite> sites = 
                        new ArrayList<ReferencedSite>(noPrefixSites.size()+prefixSites.size());
                    sites.addAll(prefixSites);
                    sites.addAll(noPrefixSites);
                    return Collections.unmodifiableCollection(sites);
                } //else the parsed entityPrefix does not start with the found prefix
                // this may only happen, when the prefixPos == prefixList.size()
            }
        }
        log.debug("No registered prefix found for entity {} " +
        		"-> return sites that accept all entities",entityUri);
        return Collections.unmodifiableCollection(noPrefixSites);
    }
    @Override
    public QueryResultList<String> findIds(FieldQuery query) {
        log.debug("findIds for query{}", query);
        // We need to search all referenced Sites
        Set<String> entityIds = new HashSet<String>();
        for(ReferencedSite site : referencedSites){
            if(site.supportsSearch()){
                log.debug(" > query site {}",site.getId());
                try {
                    for(String entityId : site.findReferences(query)){
                        entityIds.add(entityId);
                    }
                } catch (ReferencedSiteException e) {
                    log.warn("Unable to access Site "+site.getConfiguration().getName()+
                        " (id = "+site.getId()+")",e);
                }
            } else {
                log.debug(" > Site {} does not support queries",site.getId());
            }
        }
        return new QueryResultListImpl<String>(query, entityIds.iterator(),String.class);
    }
    @Override
    public QueryResultList<Representation> find(FieldQuery query) {
        log.debug("find with query{}", query);
        Set<Representation> representations = new HashSet<Representation>();
        for(ReferencedSite site : referencedSites){
            if(site.supportsSearch()){
                log.debug(" > query site {}",site.getId());
                try {
                    for(Representation rep : site.find(query)){
                        if(!representations.contains(rep)){ //do not override
                            representations.add(rep);
                        } else {
                            log.info("Entity {} found on more than one Referenced Site" +
                            		" -> Representation of Site {} is ignored",
                            		rep.getId(),site.getConfiguration().getName());
                        }
                    }
                } catch (ReferencedSiteException e) {
                    log.warn("Unable to access Site "+site.getConfiguration().getName()+
                        " (id = "+site.getId()+")",e);
                }
            } else {
                log.debug(" > Site {} does not support queries",site.getId());
            }
        }
        return new QueryResultListImpl<Representation>(query, representations,Representation.class);
    }
    @Override
    public QueryResultList<Entity> findEntities(FieldQuery query) {
        log.debug("findEntities for query{}", query);
        Set<Entity> entities = new HashSet<Entity>();
        for(ReferencedSite site : referencedSites){
            if(site.supportsSearch()){ //do not search on sites that do not support it
                log.debug(" > query site {}",site.getId());
                try {
                    for(Entity rep : site.findEntities(query)){
                        if(!entities.contains(rep)){ //do not override
                            entities.add(rep);
                        } else {
                            //TODO: find a solution for this problem
                            //      e.g. allow to add the site for entities
                            log.info("Entity {} found on more than one Referenced Site" +
                            		" -> Representation of Site {} is ignored",
                            		rep.getId(),site.getConfiguration().getName());
                        }
                    }
                } catch (ReferencedSiteException e) {
                    log.warn("Unable to access Site "+site.getConfiguration().getName()+
                        " (id = "+site.getId()+")",e);
                }
            } else {
                log.debug(" > Site {} does not support queries",site.getId());
            }
        }
        return new QueryResultListImpl<Entity>(query, entities,Entity.class);
    }
    @Override
    public InputStream getContent(String entityId, String contentType) {
        Collection<ReferencedSite> sites = getReferencedSitesByEntityPrefix(entityId);
        if(sites.isEmpty()){
            log.info("No Referenced Site registered for Entity {}",entityId);
            log.debug("Registered Prefixes {}",prefixList);
            return null;
        }
        for(ReferencedSite site : sites){
            InputStream content;
            try {
                content = site.getContent(entityId, contentType);
                if(content != null){
                    log.debug("Return Content of type {} for Entity {} from referenced site {}",
                        new Object[]{contentType,entityId,site.getConfiguration().getName()});
                    return content;
                }
            } catch (ReferencedSiteException e) {
                log.warn("Unable to access Site "+site.getConfiguration().getName()+
                    " (id = "+site.getId()+")",e);
            }

        }
        log.debug("Entity {} not found on any of the following Sites {}",entityId,sites);
        return null;
    }
    @Override
    public Entity getEntity(String entityId) {
        Collection<ReferencedSite> sites = getReferencedSitesByEntityPrefix(entityId);
        if(sites.isEmpty()){
            log.info("No Referenced Site registered for Entity {}",entityId);
            log.debug("Registered Prefixes {}",prefixList);
            return null;
        }
        for(ReferencedSite site : sites){
            Entity entity;
            try {
                entity = site.getEntity(entityId);
                if(entity != null){
                    log.debug("Return Representation of Site {} for Entity {}",
                        site.getConfiguration().getName(),entityId);
                    return entity;
                }
            } catch (ReferencedSiteException e) {
                log.warn("Unable to access Site "+site.getConfiguration().getName()+
                    " (id = "+site.getId()+")",e);
            }

        }
        log.debug("Entity {} not found on any of the following Sites {}",entityId,sites);
        return null;
    }



}
