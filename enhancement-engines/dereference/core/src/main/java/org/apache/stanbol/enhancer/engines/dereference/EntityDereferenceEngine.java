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
package org.apache.stanbol.enhancer.engines.dereference;

import static org.apache.stanbol.enhancer.engines.dereference.DereferenceConstants.URI_PATTERN;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.DC_LANGUAGE;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.ENHANCER_ENTITY_REFERENCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.commons.lang.StringUtils;
import org.apache.stanbol.commons.stanboltools.offline.OfflineMode;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.enhancer.servicesapi.helper.EnhancementEngineHelper;
import org.apache.stanbol.enhancer.servicesapi.rdf.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityDereferenceEngine implements EnhancementEngine, ServiceProperties, DereferenceConstants {

    private final Logger log = LoggerFactory.getLogger(EntityDereferenceEngine.class);
    
    /**
     * By default the EntityDereferenceEngine does use {@link ServiceProperties#ORDERING_POST_PROCESSING}
     */
    public static final int DEFAULT_ENGINE_ORDERING = ServiceProperties.ORDERING_POST_PROCESSING;
    
    /**
     * If the offline mode is enabled enforced for dereferencing Entities
     */
    private boolean offline;
    
    protected final EntityDereferencer dereferencer;
    
    protected final DereferenceEngineConfig config;
    
    protected final String name;
    
    protected final boolean filterContentLanguages;
    
    protected final boolean filterAcceptLanguages;
    
    protected final boolean uriFilterPresent;
    
    protected final List<String> prefixList;
    
    protected final List<Pattern> patternList;
    
    protected final boolean fallbackMode;
    /**
     * The Map holding the {@link #serviceProperties} for this engine.
     */
    protected final Map<String,Object> serviceProperties = new HashMap<String,Object>();
    /**
     * Unmodifiable view over {@link #serviceProperties} returned by
     * {@link #getServiceProperties()}
     */
    private final Map<String,Object> unmodServiceProperties = Collections.unmodifiableMap(serviceProperties);
    
    public EntityDereferenceEngine(EntityDereferencer dereferencer, DereferenceEngineConfig config){
        if(config == null){
            throw new IllegalArgumentException("The parsed DereferenceEngineConfig MUST NOT be NULL!");
        }
        this.config = config;
        this.name = config.getEngineName();
        log.debug("create {} name {}", getClass().getSimpleName(), name);
        this.filterContentLanguages = config.isFilterContentLanguages();
        log.debug(" - filter content languages: {}", filterContentLanguages);
        this.filterAcceptLanguages = config.isFilterAcceptLanguages();
        log.debug(" - filter Accept languages: {}", filterAcceptLanguages);
        if(dereferencer == null){
            throw new IllegalArgumentException("The parsed EntityDereferencer MUST NOT be NULL!");
        }
        this.dereferencer = dereferencer;
        log.debug(" - dereferenced {} (type: {})", dereferencer, dereferencer.getClass().getName());
        //init the default ordering
        this.fallbackMode = config.isFallbackMode();
        log.debug(" - fallback Mode: {}", fallbackMode);
        //Set the default engine ordering based on the fallback mode state:
        //in case of fallback mode call this after dereferencing engines 
        //without fallback mode
        setEngineOrdering(fallbackMode ? DEFAULT_ENGINE_ORDERING - 1 : 
        	DEFAULT_ENGINE_ORDERING);
        log.debug(" - engine order: {}", getEngineOrdering());
        //sort the prefixes
        prefixList = config.getUriPrefixes();
        if(prefixList.size() > 1){
        	Collections.sort(prefixList);
        }
        if(log.isDebugEnabled()){
        	log.debug(" - configured prefixes:");
        	for(String prefix : prefixList){
        		log.debug("     {}",prefix);
        	}
        }
        //compile the patterns
        patternList = new ArrayList<Pattern>();
        for(String pattern : config.getUriPatterns()){
        	try {
        		patternList.add(Pattern.compile(pattern));
        	} catch (PatternSyntaxException e){
        		throw new IllegalStateException("Unable to compile URI pattern '"
        				+ pattern + "' pared via property '" + URI_PATTERN + "'!");
        	}
        }
        if(log.isDebugEnabled()){
        	log.debug(" - configured patterns:");
        	for(Pattern pattern : patternList){
        		log.debug("     {}",pattern);
        	}
        }
        uriFilterPresent = !prefixList.isEmpty() || !patternList.isEmpty();
    }
    
    /**
     * Setter for the offline mode. This method is typically called of
     * {@link OfflineMode} is injected to the component registering an instance
     * of this Engine implementation
     * @param mode the offline mode
     */
    public void setOfflineMode(boolean mode){
        this.offline = mode;
    }
    
    public boolean isOfflineMode(){
        return offline;
    }
    /**
     * Setter for the {@link ServiceProperties#ENHANCEMENT_ENGINE_ORDERING
     * engine ordering}.
     * @param ordering The ordering or <code>null</code> to set the 
     * {@value #DEFAULT_ENGINE_ORDERING default} for this engine.
     */
    public void setEngineOrdering(Integer ordering){
        serviceProperties.put(ServiceProperties.ENHANCEMENT_ENGINE_ORDERING, 
            ordering == null ? DEFAULT_ENGINE_ORDERING : ordering);
    }
    
    public Integer getEngineOrdering(){
        return (Integer)serviceProperties.get(ENHANCEMENT_ENGINE_ORDERING);
    }

    /**
     * Getter for the config of this engine
     * @return the Dereference Engine Configuration
     */
    public DereferenceEngineConfig getConfig() {
        return config;
    }
    
    @Override
    public Map<String,Object> getServiceProperties() {
        return unmodServiceProperties;
    }

    @Override
    public int canEnhance(ContentItem ci) throws EngineException {
        if(offline && !dereferencer.supportsOfflineMode()){
            return CANNOT_ENHANCE;
        } else {
            return ENHANCE_ASYNC;
        }
    }

    @Override
    public void computeEnhancements(ContentItem ci) throws EngineException {
        if(offline && !dereferencer.supportsOfflineMode()){
            //entity dereferencer does no longer support offline mode
            return;
        }
        log.debug("> dereference Entities for ContentItem {}", ci.getUri());
        long start = System.nanoTime();
        final DereferenceContext derefContext = new DereferenceContext(offline);
        Set<String> includedLangs = new HashSet<String>();
        //TODO: parse accept languages as soon as Enhancement properties are implemented
        final MGraph metadata = ci.getMetadata();
        Set<UriRef> referencedEntities = new HashSet<UriRef>();
        //(1) read all Entities we need to dereference from the parsed contentItem
        ci.getLock().readLock().lock();
        try {
            //parse the languages detected for the content
            if(filterContentLanguages){
                for(NonLiteral langAnno : EnhancementEngineHelper.getLanguageAnnotations(metadata)){
                    includedLangs.add(EnhancementEngineHelper.getString(metadata, langAnno, DC_LANGUAGE));
                }
            } //no content language filtering - leave contentLanguages empty
            //parse the referenced entities from the graph
            Set<UriRef> checked = new HashSet<UriRef>();
            for(UriRef referenceProperty : config.getEntityReferences()){
                Iterator<Triple> entityReferences = metadata.filter(null, referenceProperty, null);
                while(entityReferences.hasNext()){
                    Triple triple = entityReferences.next();
                    Resource entityReference = triple.getObject();
                    if((entityReference instanceof UriRef) && //only URIs
                    		checked.add((UriRef)entityReference) && //do not check a URI twice
                    		chekcFallbackMode((UriRef)entityReference, metadata) && //fallback mode
                    		checkURI((UriRef)entityReference)){ //URI prefixes and patterns
                        boolean added = referencedEntities.add((UriRef)entityReference);
                        if(added && log.isTraceEnabled()){
                            log.trace("  ... schedule Entity {} (referenced-by: {})", 
                                entityReference, referenceProperty);
                        }
                    } else if(log.isTraceEnabled()){
                        log.trace(" ... ignore Entity {} (referenced-by: {})",
                            entityReferences, referenceProperty);
                    }
                }
            }
        } finally {
            ci.getLock().readLock().unlock();
        }
        long schedule = System.nanoTime();
        if(!includedLangs.isEmpty()){
            includedLangs.add(null); //also include literals without language
            //and set the list to the dereference context
            derefContext.setLanguages(includedLangs);
        } //else no filterLanguages set ... nothing to do

        final Lock writeLock = ci.getLock().writeLock();
        log.trace(" - scheduled {} Entities for dereferencing", 
            referencedEntities.size());
        //(2) dereference the Entities
        ExecutorService executor = dereferencer.getExecutor();
        Set<UriRef> failedEntities = new HashSet<UriRef>();
        int dereferencedCount = 0;
        List<DereferenceJob> dereferenceJobs = new ArrayList<DereferenceJob>(
                referencedEntities.size());
        if(executor != null && !executor.isShutdown()){ //dereference using executor
            //schedule all entities to dereference
            for(final UriRef entity : referencedEntities){
                DereferenceJob dereferenceJob = new DereferenceJob(entity, 
                    metadata, writeLock, derefContext);
                dereferenceJob.setFuture(executor.submit(dereferenceJob));
                dereferenceJobs.add(dereferenceJob);
            }
            //wait for all entities to be dereferenced
            for(DereferenceJob dereferenceJob : dereferenceJobs){
                try {
                    if(dereferenceJob.await()){
                        dereferencedCount++;
                    }
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                    throw new EngineException(this, ci, 
                        "Interupted while waiting for dereferencing Entities", e);
                } catch (ExecutionException e) {
                    if(e.getCause() instanceof DereferenceException){
                        failedEntities.add(dereferenceJob.entity);
                        log.debug(" ... error while dereferencing " 
                            + dereferenceJob.entity + "!", e);
                    } else { //unknown error
                        throw new EngineException(this,ci, "Unchecked Error while "
                            + "dereferencing Entity " + dereferenceJob.entity
                            + "!", e);
                    }
                }
            }
        } else { //dereference using the current thread
            for(UriRef entity : referencedEntities){
                try {
                    log.trace("  ... dereference {}", entity);
                    if(dereferencer.dereference(entity, metadata, writeLock, derefContext)){
                        dereferencedCount++;
                        log.trace("    + success");
                    } else {
                        log.trace("    - not found");
                    }
                } catch (DereferenceException e) {
                    log.debug(" ... error while dereferencing " + entity + "!", e);
                    failedEntities.add(entity);
                }
            }
        }
        long end = System.nanoTime();
        float sheduleDuration = ((schedule - start)/10000)/100f;
        float dereferenceDuration = ((end - schedule)/10000)/100f;
        float duration = ((end - start)/10000)/100f;
        if(!failedEntities.isEmpty()){
            log.warn(" - unable to dereference {} of {} for ContentItem {}",
                new Object[] {failedEntities.size(),referencedEntities.size(), 
                    ci.getUri()});
        }
        if(log.isDebugEnabled() && dereferencedCount > 0){
            log.debug(" - dereferenced {} of {} Entities in {}ms | schedule:{}ms | "
            		+ " dereference: {}ms ({}ms/entity)", new Object[]{
            				dereferencedCount, referencedEntities.size(),
            				duration, sheduleDuration, dereferenceDuration,
            				dereferenceDuration/dereferencedCount});
        }
        
    }

	@Override
    public String getName() {
        return name;
    }

    protected boolean chekcFallbackMode(UriRef entityReference, MGraph metadata) {
		return fallbackMode ? //in case we use fallback mode
				//filter entities for those an outgoing relation is present
				!metadata.filter(entityReference, null, null).hasNext() :
					true; //otherwise process all entities
	}
    /**
     * Checks if we need to schedule an Entity based on its URI. This uses
     * configured URI prefixes and URI patterns.
     * @param entity the entity to check
     * @return <code>true</code> if this entity should be scheduled for
     * dereferencing. <code>false</code> if not.
     */
    protected boolean checkURI(UriRef entity){
    	if(!uriFilterPresent){ //if no prefix nor pattern is set
    		return true; //accept all
    	}
    	//first prefixes as this is faster
    	String entityUri = entity.getUnicodeString();
    	log.trace(" - checkURI {}", entityUri);
    	//(1) check against prefixes
    	if(!prefixList.isEmpty()){
        	//as we do not want to check with all configured prefixes let us do a
        	//binary search for the correct one
	    	int pos = Collections.binarySearch(prefixList, entityUri);
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
		    	int prefixPos = Math.abs(pos)-2;
		    	if(prefixPos >= 0){
			    	String prefix = prefixList.get(prefixPos);
			    	if(entityUri.startsWith(prefix)){
			    		log.trace(" ... matched prefix {}", prefix);
			    		return true; //it matches a prefix in the list
			    	} else { //try configured regex pattern
			    		log.trace("  ... no match for prefix {}", prefix);
			    	}
		    	} else { //try configured regex pattern
		    		log.trace("  ... no prefix matches");
		    	}
		    } else {
		        return true; //entityUri found in list
		    }
    	}
	    //(2) check against regex
    	if(!patternList.isEmpty()){
    		for(Pattern pattern : patternList){
    			Matcher m = pattern.matcher(entityUri);
    			if(m.find()){
    				if(log.isTraceEnabled()) {
    					log.trace("  ... matches pattern {}", pattern);
    				}
    				return true;
    			} else if(log.isTraceEnabled()){ //try the next pattern
					log.trace("  ... no match for pattern {}", pattern);
    			}
    		}
    	}
    	return false; //no match
    }
    
    
    /**
     * Used both as {@link Callable} submitted to the {@link ExecutorService}
     * and as object to {@link #await()} the completion of the task.
     * @author Rupert Westenthaler
     *
     */
    class DereferenceJob implements Callable<Boolean> {
        
        final UriRef entity;
        final MGraph metadata;
        final Lock writeLock;
        final DereferenceContext derefContext;

        private Future<Boolean> future;
        
        DereferenceJob(UriRef entity, MGraph metadata, Lock writeLock, 
            DereferenceContext derefContext){
            this.entity = entity;
            this.metadata = metadata;
            this.writeLock = writeLock;
            this.derefContext = derefContext;
        }
        
        @Override
        public Boolean call() throws DereferenceException {
            log.trace("  ... dereference {}", entity);
            boolean state = dereferencer.dereference(entity, metadata, writeLock, derefContext);
            if(state){
                log.trace("    + success");
            } else {
                log.trace("    - not found");
            }
            return state;
        }

        void setFuture(Future<Boolean> future){
            this.future = future;
        }
        
        public boolean await() throws InterruptedException, ExecutionException {
            return future.get();
        }
    }
    
}
