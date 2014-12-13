/*
* Copyright 2013 Massachusetts General Hospital
*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.annotopia.grails.connectors.plugin.pubmed

import org.annotopia.grails.connectors.BaseConnectorController
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject


/**
* @author Paolo Ciccarese <paolo.ciccarese@gmail.com>
*/
class PubmedController extends BaseConnectorController {

	final def PUBMED_ID = "pubmedId";
	final def PUBMED_IDS = "pubmedIds";
	final def PUBMED_CENTRAL_ID = "pubmedCentralId";
	final def PUBMED_CENTRAL_IDS = "pubmedCentralIds";
	final def DOIS = "dois";
	
	def jsonPubmedAccessService;
	def apiKeyAuthenticationService;
	
	// TODO Exception management
	/**
	 * Allows only single identifiers requests: PUBMED_ID or PUBMED_CENTRAL_ID.
	 * Multiple requests should go through the entries method.
	 * 
	 * http://localhost:8080/CnPubMedConnector/pubmed/entry?format=json&typeQuery=pubmedCentralId&textQuery=PMC2700002
	 * curl -i -X GET http://localhost:8090/cn/pubmed/entry -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"pubmedCentralId","textQuery":"PMC2700002"}'
	 * curl -i -X GET http://localhost:8090/cn/pubmed/entry -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"dois","textQuery":"10.1016/j.nbd.2007.04.009"}'
	 */
	def entry = {
		long startTime = System.currentTimeMillis();
		
		// retrieve the API key
		def apiKey = retrieveApiKey(startTime);
		if(!apiKey) {
			return;
		}
		
		def typeQuery = retrieveValue(request.JSON.typeQuery, params.typeQuery,
			"typeQuery", startTime);
		if(!typeQuery) {
			return;
		}
		
		def textQuery = retrieveValue(request.JSON.textQuery, params.textQuery,
			"textQuery", startTime);
		if(!textQuery) {
			return;
		}
		
		log.info("PubMed entry request typeQuery: " + typeQuery + " | textQuery: "+ textQuery);
		
		JSONArray json = new JSONArray();
		if(typeQuery.trim().equals(PUBMED_ID)) {			
			JSONObject jsonObject = jsonPubmedAccessService.getPubmedArticle(textQuery);
			if(jsonObject!=null) json.add(jsonObject);
			else log.warn "No record returned for PubMed id: " + textQuery;
			render(contentType:'text/json', text: json.toString())
		} else if(typeQuery.trim().equals(PUBMED_CENTRAL_ID) || typeQuery.trim().equals(DOIS)) {
			StringTokenizer st = new StringTokenizer(textQuery,",");
			List<String> ids = new ArrayList<String>();
			while (st.hasMoreTokens()) {
				ids.add(st.nextToken());
			}
			json = jsonPubmedAccessService.searchPubmedArticles(typeQuery, ids);
			render(contentType:'text/json', text: json.toString());
		} else {
			log.warn("entry() cannot execute a query of type: " + typeQuery);
			render(contentType:'text/json', text: new JSONArray());
		}
	}
	
	/**
	 * Returns the PubMed json records for the entries identified by the PubMed  
	 * comma separated ids in the textQuery.
	 * 
	 * curl -i -X GET http://localhost:8090/cn/pubmed/entries -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"pubmedIds","textQuery":"25093072"}'
	 * curl -i -X GET http://localhost:8090/cn/pubmed/entries -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"pubmedCentralId","textQuery":"PMC2700002"}'
	 */
	def entries = {	
		long startTime = System.currentTimeMillis();
		
		// retrieve the API key
		def apiKey = retrieveApiKey(startTime);
		if(!apiKey) {
			return;
		}
		
		def typeQuery = retrieveValue(request.JSON.typeQuery, params.typeQuery,
			"typeQuery", startTime);
		if(!typeQuery) {
			return;
		}
		
		def textQuery = retrieveValue(request.JSON.textQuery, params.textQuery,
			"textQuery", startTime);
		if(!textQuery) {
			return;
		}
		
		log.info("PubMed entries request typeQuery: " + typeQuery + " | textQuery: "+ textQuery);
		
		JSONArray json = new JSONArray();
		if(typeQuery.equals(PUBMED_IDS)) {
			StringTokenizer st = new StringTokenizer(textQuery, ",");
			List<String> pmids = new ArrayList<String>();
			while(st.hasMoreTokens()) {
				pmids.add(st.nextToken())
			}
			json = jsonPubmedAccessService.getPubmedArticles(pmids);
			render(contentType:'text/json', text: json.toString())
		} else if(typeQuery.trim().equals(PUBMED_CENTRAL_ID)) {
			StringTokenizer st = new StringTokenizer(textQuery,",");
			List<String> ids = new ArrayList<String>();
			while (st.hasMoreTokens()) {
				ids.add(st.nextToken());
			}
			json = jsonPubmedAccessService.searchPubmedArticles(typeQuery, ids);
			render(contentType:'text/json', text: json.toString());
		} else {
			log.warn("entries() cannot execute a query of type: " + typeQuery);
			render(contentType:'text/json', text: new JSONArray());
		}
	}
	
	/**
	 * curl -i -X GET http://localhost:8090/cn/pubmed/search -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"title","textQuery":"Annotation Web 3.0"}'
	 * curl -i -X GET http://localhost:8090/cn/pubmed/search -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"title","textQuery":"Annotation","maxResults":"2","offset":"0"}'
	 * curl -i -X GET http://localhost:8090/cn/pubmed/search -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","typeQuery":"title","textQuery":"Annotation"}'
	 */
	def search = {
		long startTime = System.currentTimeMillis();
		
		// retrieve the API key
		def apiKey = retrieveApiKey(startTime);
		if(!apiKey) {
			return;
		}
		
		def typeQuery = retrieveValue(request.JSON.typeQuery, params.typeQuery,
			"typeQuery", startTime);
		if(!typeQuery) {
			return;
		}
		
		def textQuery = retrieveValue(request.JSON.textQuery, params.textQuery,
			"textQuery", startTime);
		if(!textQuery) {
			return;
		}
		
		if(textQuery==null) { // If text query is empty return empty results list
			render(contentType:'text/json', text: '{"total":"0","results":[],"exception":"Text query is empty"}');
			return;
		} else { // Else trim the query text
			textQuery = textQuery.trim();
		}

		int startMonth = Integer.parseInt(retrieveValue(request.JSON.type, params.type, "-1"));
		int startYear = Integer.parseInt(retrieveValue(request.JSON.type, params.type, "-1"));
		int endMonth = Integer.parseInt(retrieveValue(request.JSON.type, params.type, "-1"));
		int endYear = Integer.parseInt(retrieveValue(request.JSON.type, params.type, "-1"));
		
		int maxResults = Integer.parseInt(retrieveValue(request.JSON.maxResults, params.maxResults, "-1"));
		int offset = Integer.parseInt(retrieveValue(request.JSON.offset, params.offset, "-1"));
		
		log.info("PubMed search request typeQuery: " + typeQuery + " | textQuery: "+ textQuery + " | maxResults: " + maxResults+ " | offset: " + offset);
		
		StringTokenizer st = new StringTokenizer(textQuery, ",");
		List<String> queryTerms = new ArrayList<String>();
		while(st.hasMoreTokens()) {
			queryTerms.add(st.nextToken().trim())
		}

		JSONObject json = jsonPubmedAccessService.searchPubmedArticlesWithStats(typeQuery, queryTerms, startMonth, startYear, endMonth, endYear, maxResults, offset);
		render(contentType:'text/json', text: json.toString())
	}
}
