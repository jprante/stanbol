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
package org.apache.stanbol.enhancer.jobmanager.event.impl;

import static org.apache.stanbol.enhancer.jobmanager.event.Constants.PROPERTY_EXECUTION;
import static org.apache.stanbol.enhancer.jobmanager.event.Constants.PROPERTY_JOB_MANAGER;
import static org.apache.stanbol.enhancer.jobmanager.event.Constants.TOPIC_JOB_MANAGER;
import static org.apache.stanbol.enhancer.servicesapi.helper.ExecutionPlanHelper.getEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngineManager;
import org.apache.stanbol.enhancer.servicesapi.helper.ExecutionPlanHelper;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancementJobHandler implements EventHandler {

    private EnhancementEngineManager engineManager;
    private EventAdmin eventAdmin;

    /*
     * NOTE on debug level Loggings
     * 
     *  ++ ... init some activity
     *  >> ... started some activity (thread has the requested lock)
     *  << ... completed some activity (thread has released the lock)
     *  
     *  n: ... no lock
     *  r: ... read lock
     *  w: ... write lock
     */
    private Logger log = LoggerFactory.getLogger(EnhancementJobHandler.class);
    /**
     * Keys are {@link EnhancementJob}s currently asynchronously enhancing 
     * contentItems and the values are the objects used to interrupt the 
     * requesting thread as soon as the enhancement process has finished. 
     */
    private Map<EnhancementJob,Object> processingJobs;
    private final ReadWriteLock processingLock = new ReentrantReadWriteLock();
    private Thread observerDaemon;
    
    public EnhancementJobHandler(EventAdmin eventAdmin, 
                                 EnhancementEngineManager engineManager) {
        if(eventAdmin == null){
            throw new IllegalArgumentException("The parsed EventAdmin service MUST NOT be NULL!");
        }
        if(engineManager == null){
            throw new IllegalArgumentException("The parsed EnhancementEngineManager MUST NOT be NULL!");
        }
        this.eventAdmin = eventAdmin;
        this.engineManager = engineManager;
        processingLock.writeLock().lock();
        try {
            processingJobs = new LinkedHashMap<EnhancementJob,Object>();
        } finally{
            processingLock.writeLock().unlock();
        }
        observerDaemon = new Thread(new EnhancementJobObserver());
        observerDaemon.setName("Event Job Manager Observer Daemon");
        observerDaemon.setDaemon(true);
        observerDaemon.start();
        
    }
    /**
     * Closes this Handler and notifies all components that wait for still
     * running jobs
     */
    public void close(){
        log.info("deactivate {}",getClass().getName());
        processingLock.writeLock().lock();
        try {
            for(Object o : processingJobs.values()){
                synchronized (o) {
                    o.notifyAll();
                }
            }
            processingJobs = null;
        } finally {
            processingLock.writeLock().unlock();
        }
        observerDaemon = null;
    }
    
    /**
     * Registers an EnhancementJob and will start the enhancement process.
     * When the process is finished or this service is deactivated the
     * returned oject will be notified. Therefore callers that need to 
     * wait for the completion of the parsed job will want to
     * <code><pre>
     *   Object object = enhancementJobHandler.register();
     *   while(!job.isFinished() & enhancementJobHandler != null){
     *       synchronized (object) {
     *           try {
     *               object.wait();
     *           } catch (InterruptedException e) {}
     *       }
     *   }
     * </pre></code>
     * @param enhancementJob the enhancement job to register
     * @return An object that will get {@link Object#notifyAll()} as soon as
     * {@link EnhancementJob#isFinished()} or this instance is deactivated
     */
    public Object register(EnhancementJob enhancementJob){
        final boolean init;
        Object o;
        processingLock.writeLock().lock();
        try {
            if(enhancementJob == null || processingJobs == null){
                return null;
            }
            o = processingJobs.get(enhancementJob);
            if(o == null){
                o = new Object();
                logJobInfo(enhancementJob, "Add EnhancementJob:");
                processingJobs.put(enhancementJob, o);
                init = true;
            } else {
                init = false;
            }
        } finally {
            processingLock.writeLock().unlock();
        }
        if(init){
            enhancementJob.startProcessing();
            log.debug("++ w: {}","init execution");
            enhancementJob.getLock().writeLock().lock();
            try {
                log.debug(">> w: {}","init execution");
                executeNextNodes(enhancementJob);
            } finally {
                log.debug("<< w: {}","init execution");
                enhancementJob.getLock().writeLock().unlock();
            }
        }
        return o;
    }

    @Override
    public void handleEvent(Event event) {
        EnhancementJob job = (EnhancementJob)event.getProperty(PROPERTY_JOB_MANAGER);
        NonLiteral execution = (NonLiteral)event.getProperty(PROPERTY_EXECUTION);
        if(job == null || execution == null){
            log.warn("Unable to process EnhancementEvent where EnhancementJob " +
            		"{} or Execution node {} is null -> ignore",job,execution);
        }
        try {
            processEvent(job, execution);
        } catch (Throwable t) {
            String message = String.format("Unexpected Exception while processing " +
            		"ContentItem %s with EnhancementJobManager: %s",
                    job.getContentItem().getUri(),EventJobManagerImpl.class);
            //this ensures that an runtime exception does not 
           job.setFailed(execution, null, new IllegalStateException(message,t));
           log.error(message,t);
        }
        //(2) trigger the next actions
        log.debug("++ w: {}","check for next Executions");
        job.getLock().writeLock().lock();
        log.debug(">> w: {}","check for next Executions");
        try {
            if(job.isFinished()){
                finish(job);
            } else if(!job.isFailed()){
                if(!executeNextNodes(job) && job.getRunning().isEmpty()){
                    log.warn("Unexpected state in the Execution of ContentItem {}:"
                        + " Job is not finished AND no executions are running AND"
                        + " no further execution could be started! -> finishing"
                        + " this job :(");
                    finish(job);
                } //else execution started of other jobs are running
            } else {
                if(log.isInfoEnabled()){
                    Collection<String> running = new ArrayList<String>(3);
                    for(NonLiteral runningNode : job.getRunning()){
                        running.add(getEngine(job.getExecutionPlan(), job.getExecutionNode(runningNode)));
                    }
                    log.info("Job {} failed, but {} still running!",
                        job.getContentItem().getUri(),running);
                }
            }
        } finally {
            log.debug("<< w: {}","check for next Executions");
            job.getLock().writeLock().unlock();
        }
    }
    /**
     * @param job
     * @param execution
     */
    private void processEvent(EnhancementJob job, NonLiteral execution) {
        NonLiteral executionNode = job.getExecutionNode(execution);
        String engineName = getEngine(job.getExecutionPlan(), executionNode);
        //(1) execute the parsed ExecutionNode
        EnhancementEngine engine = engineManager.getEngine(engineName);
        if(engine != null){
            //execute the engine
            Exception exception = null;
            int engineState;
            try {
                engineState = engine.canEnhance(job.getContentItem());
            } catch (EngineException e) {
                exception = e;
                log.warn("Unable to check if engine '" + engineName
                    + "'(type: " + engine.getClass() + ") can enhance ContentItem '"
                    + job.getContentItem().getUri()+ "'!",e);
                engineState = EnhancementEngine.CANNOT_ENHANCE;
            }
            if(engineState == EnhancementEngine.ENHANCE_SYNCHRONOUS){
                //ensure that this engine exclusively access the content item
                log.debug("++ w: {}: {}","start sync execution", engine.getName());
                job.getLock().writeLock().lock();
                log.debug(">> w: {}: {}","start sync execution", engine.getName());
                try {
                    engine.computeEnhancements(job.getContentItem());
                    job.setCompleted(execution);
                } catch (EngineException e){
                    job.setFailed(execution, engine, e);
                } finally{
                    log.debug("<< w: {}: {}","finished sync execution", engine.getName());
                    job.getLock().writeLock().unlock();
                }
            } else if(engineState == EnhancementEngine.ENHANCE_ASYNC){
                try {
                    log.debug("++ n: start async execution of Engine {}",engine.getName());
                    engine.computeEnhancements(job.getContentItem());
                    log.debug("++ n: finished async execution of Engine {}",engine.getName());
                    job.setCompleted(execution);
                } catch (EngineException e) {
                    job.setFailed(execution, engine, e);
                } catch (RuntimeException e) {
                    job.setFailed(execution, engine, e);
                }
            } else { //CANNOT_ENHANCE
                if(exception != null){
                    job.setFailed(execution,engine,exception);
                } else { //can not enhance is not an error
                    //it just says this engine can not enhance this content item
                    job.setCompleted(execution);
                }
            }
        } else { //engine with that name is not available
            job.setFailed(execution, null, null);
        }
    }
    /**
     * Removes a finished job from {@link #processingJobs} and notifies
     * all waiting components
     * @param job the finished job
     */
    private void finish(EnhancementJob job){
        processingLock.writeLock().lock();
        Object o;
        try {
            o = processingJobs.remove(job);
        } finally {
            processingLock.writeLock().unlock();
        }
        if(o != null) {
            synchronized (o) {
                logJobInfo(job, "Finished EnhancementJob:");
                log.debug("++ n: finished processing ContentItem {} with Chain {}",
                    job.getContentItem().getUri(),job.getChainName());
                o.notifyAll();
            }
        } else {
            log.warn("EnhancementJob for ContentItem {} is not " +
                    "registered with {}. Will not send notification!",
                    job.getContentItem().getUri(), getClass().getName());
        }            
    }
    /**
     * triggers the execution of the next nodes or if 
     * {@link EnhancementJob#isFinished()} notifies the one who registered 
     * the {@link EnhancementJob} with this component.
     * @param job the enhancement job to process
     * @return if an Execution event was sent
     */
    protected boolean executeNextNodes(EnhancementJob job) {
        //getExecutable returns an snapshot so we do not need to lock
        boolean startedExecution = false;
        for(NonLiteral executable : job.getExecutable()){
            if(log.isDebugEnabled()){
                log.debug("PREPARE execution of Engine {}",
                    getEngine(job.getExecutionPlan(), job.getExecutionNode(executable)));
            }
            Dictionary<String,Object> properties = new Hashtable<String,Object>();
            properties.put(PROPERTY_JOB_MANAGER, job);
            properties.put(PROPERTY_EXECUTION, executable);
            job.setRunning(executable);
            if(log.isDebugEnabled()){
                log.debug("SHEDULE execution of Engine {}",
                    getEngine(job.getExecutionPlan(), job.getExecutionNode(executable)));
            }
            eventAdmin.postEvent(new Event(TOPIC_JOB_MANAGER,properties));
            startedExecution = true;
        }
        return startedExecution;
    }
    
    /**
     * Logs basic infos about the Job as INFO and detailed infos as DEBUG
     * @param job
     */
    protected void logJobInfo(EnhancementJob job, String header) {
        if(header != null){
            log.info(header);
        }
        log.info("   state: {}",job.isFinished()?"finished":job.isFailed()?"failed":"processing");
        log.info("   chain: {}",job.getChainName());
        log.info("   content-item: {}", job.getContentItem().getUri());
        log.debug("   executions:");
        if(log.isDebugEnabled()){
            for(NonLiteral completedExec : job.getCompleted()){
                log.info("    - {} completed",getEngine(job.getExecutionMetadata(), 
                    job.getExecutionNode(completedExec)));
            }
            for(NonLiteral runningExec : job.getRunning()){
                log.info("    - {} running",getEngine(job.getExecutionMetadata(), 
                    job.getExecutionNode(runningExec)));
            }
        }
    }
    /**
     * Currently only used to debug the number of currently registered
     * Enhancements Jobs (if there are some)
     * @author Rupert Westenthaler
     */
    private class EnhancementJobObserver implements Runnable {

        @Override
        public void run() {
            log.debug(" ... init EnhancementJobObserver");
            while(processingJobs != null){
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
                Collection<EnhancementJob> jobs;
                Lock readLock = processingLock.readLock();
                readLock.lock();
                try {
                    if(processingJobs != null){
                        jobs = new ArrayList<EnhancementJob>(processingJobs.keySet());
                    } else {
                        jobs = Collections.emptyList();
                    }
                } finally {
                    readLock.unlock();
                }
                if(!jobs.isEmpty()){
                    log.info(" -- {} active Enhancement Jobs",jobs.size());
                    if(log.isDebugEnabled()){
                        for(EnhancementJob job : jobs){
                            Lock jobLock = job.getLock().readLock();
                            jobLock.lock();
                            try {
                                logJobInfo(job,null);
                            } finally {
                                jobLock.unlock();
                            }
                        }
                    }
                } else {
                    log.debug(" -- No active Enhancement Jobs");
                }
            }
            
        }
        
    }
    
    
}