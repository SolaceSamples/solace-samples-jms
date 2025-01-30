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

import com.solacesystems.jms.SolConnection;
import com.solacesystems.jms.SolConnectionEventListener;
import com.solacesystems.jms.SupportedProperty;
import com.solacesystems.jms.events.SolConnectionEvent;
import com.solacesystems.jms.events.SolReconnectingEvent;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.time.Instant;
import java.util.Hashtable;

/**
 * Demonstrates how to create a Connection configured for OpenId Connect
 */
public class HowToCreateAConnectionForOpenIdConnect {

    final static String SOLJMS_INITIAL_CONTEXT_FACTORY = "com.solacesystems.jndi.SolJNDIInitialContextFactory";
    final static String CONNECTION_FACTORY_JNDI_NAME = "/JNDI/CF/GettingStarted";

    public Connection createJmsConnectionWithOIDC(String jndiProviderURL, String myIdToken, String myOptionalAccessToken, String issuerIdentifierURL) throws NamingException, JMSException {
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
        env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
        env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_OAUTH2);
        env.put(SupportedProperty.SOLACE_JMS_OIDC_ID_TOKEN, myIdToken);
        env.put(SupportedProperty.SOLACE_JMS_OAUTH2_ACCESS_TOKEN, myOptionalAccessToken);
        env.put(SupportedProperty.SOLACE_JMS_OAUTH2_ISSUER_IDENTIFIER, issuerIdentifierURL);
        // Other properties
        // Create InitialContext.
        final InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        final ConnectionFactory cf = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);
// Create connection
        Connection connection = cf.createConnection();
        ((SolConnection) connection).setConnectionEventListener(
                new SolConnectionEventListener() {
                    @Override
                    public void onEvent(SolConnectionEvent event) {
                        if (event instanceof SolReconnectingEvent) {
                            SolReconnectingEvent reconnectEvent = (SolReconnectingEvent) event;
                            if ("RECONNECTING".equals(reconnectEvent.getType())) {
                                // This may indicate OAuth token expiration
                                System.out.println("Refreshing OAuth token");
                                // Implement your token refresh logic here
                                try {
                                    env.put(SupportedProperty.SOLACE_JMS_OAUTH2_ACCESS_TOKEN, refreshOAuthAccessToken(issuerIdentifierURL));
                                    env.put(SupportedProperty.SOLACE_JMS_OAUTH2_ISSUER_IDENTIFIER, refreshOIDCAccessToken(issuerIdentifierURL));
                                } catch (Exception e) {
                                    //Make sure to catch a specific Exception inline with the OAuth and OpenID provider you are referring to
                                    e.printStackTrace();
                                }

                            }
                        }
                    }
                }
        );
        //Return connection
        return connection;
    }

    public String refreshOAuthAccessToken(String issuerId) throws Exception {
        String newAccessToken = fetchNewAccessToken();
        if (newAccessToken == null) {
            throw new Exception("Failed to refresh OAuth token");
        }
        if (!validateTokenClaims(newAccessToken, issuerId)) {
            throw new Exception("OAuth Token validation failed");
        }
        return newAccessToken;
    }

    private String refreshOIDCAccessToken(String issuerId) throws Exception {
        String newIdToken = fetchNewOidcIdToken();
        if (newIdToken == null) {
            throw new Exception("Failed to refresh OIDC token");
        }
        if (!validateTokenClaims(newIdToken, issuerId)) {
            throw new Exception("OIDC Token validation failed");
        }
        return newIdToken;
    }

    private boolean validateTokenClaims(String token, String expectedIssuer) {
        boolean isTokenExpired = isTokenExpired(token);
        boolean isIssuerValid = isIssuerValid(token, expectedIssuer);
        boolean isAudienceValid = isAudienceValid(token);

        if (!isTokenExpired && isIssuerValid && isAudienceValid) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        // Simulate checking expiration (use a real JWT parser to check expiration claim)
        Instant now = Instant.now();
        Instant expirationTime = Instant.now().plusSeconds(3600); // Simulated expiration
        return now.isAfter(expirationTime);
    }

    private boolean isIssuerValid(String token, String expectedIssuer) {
        // Simulate checking issuer claim
        String issuer = "https://example.com"; // Simulated issuer
        return expectedIssuer.equals(issuer);
    }

    private boolean isAudienceValid(String token) {
        // Simulate checking audience claim
        String audience = "your-audience"; // Simulated audience
        return "your-audience".equals(audience);
    }

    private String fetchNewAccessToken() {
        // Simulate API call to identity provider to refresh access token
        // TODO: Implement real API call
        return "newAccessTokenFromIDP";
    }

    private String fetchNewOidcIdToken() {
        // Simulate API call to identity provider to refresh ID token
        // TODO: Implement real API call
        return "newIDTokenFromIDP";
    }
}
