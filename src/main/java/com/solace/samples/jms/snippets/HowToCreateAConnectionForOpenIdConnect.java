/*
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

package com.solace.samples.jms.snippets;

import java.util.Hashtable;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.JMSException;

import com.solacesystems.jms.SupportedProperty;

/**
 * Demonstrates how to create a Connection configured for OpenId Connect
 */
public class HowToCreateAConnectionForOpenIdConnect {

  final static String SOLJMS_INITIAL_CONTEXT_FACTORY = "com.solacesystems.jndi.SolJNDIInitialContextFactory";
  final static String CONNECTION_FACTORY_JNDI_NAME = "/JNDI/CF/GettingStarted";

  public static Connection createJmsConnectionWithOIDC(String jndiProviderURL,String myIdToken,String myOptionalAccessToken) throws  NamingException, JMSException
  {
    Hashtable<String, Object> env = new Hashtable<String, Object>();
    env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
    env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
    env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_OAUTH2);
    env.put(SupportedProperty.SOLACE_JMS_OIDC_ID_TOKEN, myIdToken);
    env.put(SupportedProperty.SOLACE_JMS_OAUTH2_ACCESS_TOKEN, myOptionalAccessToken);
    // Other properties
    // Create InitialContext.
    final InitialContext  initialContext = new InitialContext(env);
    // Lookup ConnectionFactory.
    final ConnectionFactory cf = (ConnectionFactory)initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);
    // Create and return connection.
    return cf.createConnection();
  }
    
}
