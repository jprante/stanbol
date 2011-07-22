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
package org.apache.stanbol.ontologymanager.ontonet.io;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URL;
import java.util.Hashtable;
import java.util.Set;

import org.apache.stanbol.ontologymanager.ontonet.Constants;
import org.apache.stanbol.ontologymanager.ontonet.api.ONManager;
import org.apache.stanbol.ontologymanager.ontonet.api.io.OntologyInputSource;
import org.apache.stanbol.ontologymanager.ontonet.api.io.ParentPathInputSource;
import org.apache.stanbol.ontologymanager.ontonet.api.io.RootOntologyIRISource;
import org.apache.stanbol.ontologymanager.ontonet.impl.ONManagerImpl;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.io.WriterDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.AutoIRIMapper;

public class TestOntologyInputSources {

    private static OWLDataFactory df;

    private static ONManager onm;

    @BeforeClass
    public static void setUp() {
        onm = new ONManagerImpl(null, null, null, new Hashtable<String,Object>());
        df = onm.getOwlFactory();
    }

    @Test
    public void testAutoIRIMapper() throws Exception {

        URL url = getClass().getResource("/ontologies");
        OWLOntologyIRIMapper mapper = new AutoIRIMapper(new File(url.toURI()), true);
        assertNotNull(url);

        IRI dummyiri = IRI.create("http://stanbol.apache.org/ontologies/peanuts/dummycharacters.owl");

        // Cleanup may be required if previous tests have failed.
        if (mapper.getDocumentIRI(dummyiri) != null) {
            new File(mapper.getDocumentIRI(dummyiri).toURI()).delete();
            ((AutoIRIMapper) mapper).update();
        }
        assertNull(mapper.getDocumentIRI(dummyiri));

        // Create a new ontology in the test resources.
        OWLOntologyManager mgr = onm.getOntologyManagerFactory().createOntologyManager(false);
        OWLOntology o = mgr.createOntology(dummyiri);
        File f = new File(URI.create(url.toString() + "/dummycharacters.owl"));
        mgr.saveOntology(o, new WriterDocumentTarget(new FileWriter(f)));
        assertTrue(f.exists());

        ((AutoIRIMapper) mapper).update();
        // The old mapper should be able to locate the new ontology.
        assertNotNull(mapper.getDocumentIRI(dummyiri));

        // A new mapper too
        OWLOntologyIRIMapper mapper2 = new AutoIRIMapper(new File(url.toURI()), true);
        assertNotNull(mapper2.getDocumentIRI(dummyiri));

        // cleanup
        f.delete();
    }

    /**
     * Uses a {@link ParentPathInputSource} to load an ontology importing a modified FOAF, both located in the
     * same resource directory.
     * 
     * @throws Exception
     */
    @Test
    public void testOfflineImport() throws Exception {
        URL url = getClass().getResource("/ontologies/maincharacters.owl");
        assertNotNull(url);
        File f = new File(url.toURI());
        assertNotNull(f);
        OntologyInputSource coreSource = new ParentPathInputSource(f);

        // Check that all the imports closure is made of local files
        Set<OWLOntology> closure = coreSource.getClosure();
        for (OWLOntology o : closure)
            assertEquals("file", o.getOWLOntologyManager().getOntologyDocumentIRI(o).getScheme());

        assertEquals(coreSource.getRootOntology().getOntologyID().getOntologyIRI(),
            IRI.create(Constants.PEANUTS_MAIN_BASE));
        // Linus is stated to be a foaf:Person
        OWLNamedIndividual iLinus = df.getOWLNamedIndividual(IRI.create(Constants.PEANUTS_MAIN_BASE
                                                                        + "#Linus"));
        // Lucy is stated to be a foaf:Perzon
        OWLNamedIndividual iLucy = df
                .getOWLNamedIndividual(IRI.create(Constants.PEANUTS_MAIN_BASE + "#Lucy"));
        OWLClass cPerzon = df.getOWLClass(IRI.create("http://xmlns.com/foaf/0.1/Perzon"));

        Set<OWLIndividual> instances = cPerzon.getIndividuals(coreSource.getRootOntology());
        assertTrue(!instances.contains(iLinus) && instances.contains(iLucy));
    }

    /**
     * Loads a modified FOAF by resolving a URI from a resource directory.
     * 
     * @throws Exception
     */
    @Test
    public void testOfflineSingleton() throws Exception {
        URL url = getClass().getResource("/ontologies/mockfoaf.rdf");
        assertNotNull(url);
        OntologyInputSource coreSource = new RootOntologyIRISource(IRI.create(url));
        assertNotNull(df);
        /*
         * To check it fetched the correct ontology, we look for a declaration of the bogus class foaf:Perzon
         * (added in the local FOAF)
         */
        OWLClass cPerzon = df.getOWLClass(IRI.create("http://xmlns.com/foaf/0.1/Perzon"));
        assertTrue(coreSource.getRootOntology().getClassesInSignature().contains(cPerzon));
    }

}