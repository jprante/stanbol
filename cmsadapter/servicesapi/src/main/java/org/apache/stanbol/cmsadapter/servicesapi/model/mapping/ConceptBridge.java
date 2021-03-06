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
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.3-hudson-jaxb-ri-2.2.3-3- 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2011.05.09 at 02:52:53 PM EEST 
//


package org.apache.stanbol.cmsadapter.servicesapi.model.mapping;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Query" type="{mapping.model.servicesapi.cmsadapter.stanbol.apache.org}QueryType"/>
 *         &lt;element ref="{mapping.model.servicesapi.cmsadapter.stanbol.apache.org}SubsumptionBridge" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{mapping.model.servicesapi.cmsadapter.stanbol.apache.org}PropertyBridge" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "query",
    "subsumptionBridge",
    "propertyBridge"
})
@XmlRootElement(name = "ConceptBridge")
public class ConceptBridge {

    @XmlElement(name = "Query", required = true)
    protected String query;
    @XmlElement(name = "SubsumptionBridge")
    protected List<SubsumptionBridge> subsumptionBridge;
    @XmlElement(name = "PropertyBridge")
    protected List<PropertyBridge> propertyBridge;

    /**
     * Gets the value of the query property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getQuery() {
        return query;
    }

    /**
     * Sets the value of the query property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setQuery(String value) {
        this.query = value;
    }

    /**
     * Gets the value of the subsumptionBridge property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subsumptionBridge property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubsumptionBridge().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SubsumptionBridge }
     * 
     * 
     */
    public List<SubsumptionBridge> getSubsumptionBridge() {
        if (subsumptionBridge == null) {
            subsumptionBridge = new ArrayList<SubsumptionBridge>();
        }
        return this.subsumptionBridge;
    }

    /**
     * Gets the value of the propertyBridge property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the propertyBridge property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPropertyBridge().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PropertyBridge }
     * 
     * 
     */
    public List<PropertyBridge> getPropertyBridge() {
        if (propertyBridge == null) {
            propertyBridge = new ArrayList<PropertyBridge>();
        }
        return this.propertyBridge;
    }

}
