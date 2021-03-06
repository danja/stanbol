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
package org.apache.stanbol.ontologymanager.sources.clerezza;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.access.TcProvider;
import org.apache.stanbol.ontologymanager.servicesapi.io.OntologyInputSource;
import org.apache.stanbol.ontologymanager.servicesapi.io.Origin;

/**
 * An {@link OntologyInputSource} that gets ontologies from either a stored {@link TripleCollection}, or its
 * identifier and an optionally supplied triple collection manager.
 * 
 * @author alexdma
 * 
 */
public class GraphSource extends AbstractClerezzaGraphInputSource {

    /**
     * Creates a new input source by querying the default triple collection manager for a graph named with the
     * supplied <code>graphId</code>. A {@link UriRef} that represents the graph name will also be set as the
     * graph origin.
     * 
     * @param graphId
     *            the graph ID.
     * @throws NullPointerException
     *             if there is no default triple collection manager available.
     * @throws org.apache.clerezza.rdf.core.access.NoSuchEntityException
     *             if no such graph can be found.
     */
    public GraphSource(String graphId) {
        this(new UriRef(graphId));
    }

    /**
     * Wraps the supplied <code>graph</code> into a new input source. No origin will be set.
     * 
     * @param graph
     *            the RDF graph
     * @throws IllegalArgumentException
     *             if <code>graph</code> is neither a {@link Graph} nor a {@link MGraph}.
     */
    public GraphSource(TripleCollection graph) {
        if (graph instanceof Graph) bindRootOntology(graph);
        else if (graph instanceof MGraph) bindRootOntology(((MGraph) graph).getGraph());
        else throw new IllegalArgumentException("GraphSource supports only Graph and MGraph types. "
                                                + graph.getClass() + " is not supported.");
        bindPhysicalOrigin(null);
    }

    /**
     * Creates a new input source by querying the default triple collection manager for a graph named with the
     * supplied <code>graphId</code>. The supplied ID will also be set as the graph origin.
     * 
     * @param graphId
     *            the graph ID.
     * @throws NullPointerException
     *             if there is no default triple collection manager available.
     * @throws org.apache.clerezza.rdf.core.access.NoSuchEntityException
     *             if no such graph can be found.
     */
    public GraphSource(UriRef graphId) {
        this(graphId, TcManager.getInstance());
    }

    /**
     * Creates a new input source by querying the supplied triple collection provider for a graph named with
     * the supplied <code>graphId</code>. The supplied ID will also be set as the graph origin.
     * 
     * @param graphId
     *            the graph ID.
     * @throws NullPointerException
     *             if <code>tcProvider</code> is null.
     * @throws org.apache.clerezza.rdf.core.access.NoSuchEntityException
     *             if no such graph can be found in <code>tcProvider</code>.
     */
    public GraphSource(UriRef graphId, TcProvider tcProvider) {
        this(tcProvider.getTriples(graphId));
        bindPhysicalOrigin(Origin.create(graphId));
    }

    @Override
    public String toString() {
        return "GRAPH<" + rootOntology.getClass() + "," + getOrigin() + ">";
    }

}
