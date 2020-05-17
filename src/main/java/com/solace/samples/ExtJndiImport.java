/**
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

/**
 *  Solace JMS 1.1 Examples: ExtJndiImport
 */

package com.solace.samples;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NotContextException;
import javax.naming.Reference;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolQueue;
import com.solacesystems.jms.SolTopic;

/**
 * Imports setting for JMS ConnectionFactory and Queue objects from Solace internal JNDI server
 * to an external JNDI server.
 *
 * Requires both Solace and external JNDI connection paramters and source and target JNDI names with object type.
 */
public class ExtJndiImport {
    // Operation to perform
    public enum Operation {
        BIND, // adds an element to the JNDI
        REBIND, // replaces an element in the JNDI
        UNBIND, // removes an element from the JNDI
        LIST // lists elements in the JNDI
    };

    // External JNDI Initial Context Factory
    private static final String EXTJNDI_INITIAL_CONTEXT_FACTORY = 
    "com.sun.jndi.fscontext.RefFSContextFactory";
    // Other provider example for LDAP:
    // "com.sun.jndi.ldap.LdapCtxFactory";  // LDAP

    // Solace parameters
    private String solaceUrl = null;
    private String solaceUsername = null;
    private String solacePassword = null;
    
    // The Url of the JNDI server
    private String extJndiUrl = null;

    // Username used to log into the JNDI server
    private String extJndiUsername = null;

    // Password used to log into the JNDI server
    private String extJndiPassword = null;

    // the bind operation to perform
    private Operation operation = null;

    // the object to bind - a connection factory, topic, or queue
    private String sourceCfJndiName = null;
    private String sourceTopicJndiName = null;
    private String sourceQueueJndiName = null;

    // The distinguished name of the element to bind
    private String name = null;

    private void printUsage() {
        System.out.println(
                "\nUsage: \nSolJMSJNDIBind -solaceUrl S_Url -solaceUsername S_USERNAME@VPN -solacePassword S_PASSWORD"
                        + " -jndiUrl J_Url -jndiUsername J_USERAME -jndiPassword J_PASSWORD"
                        + " -operation OPERATION [-cf CF] [-topic TOPIC] [-queue QUEUE] -name DN"
                        + "\nWhere:\n" + "- OPERATION  is one of [BIND, REBIND, UNBIND, LIST]\n");
    }

    private void run() {
        Context extJndiInitialContext = null;
        Context solInitialContext = null;
        try {
            // Create the external JNDI Initial Context
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, EXTJNDI_INITIAL_CONTEXT_FACTORY);
            env.put(Context.PROVIDER_URL, extJndiUrl);
            env.put(Context.REFERRAL, "throw");
            env.put(Context.SECURITY_PRINCIPAL, extJndiUsername);
            env.put(Context.SECURITY_CREDENTIALS, extJndiPassword);
            extJndiInitialContext = new InitialContext(env);
            // Handle UNBIND or LIST operations first
            if (operation.equals(Operation.UNBIND)) {
                try {
                    NamingEnumeration<NameClassPair> enumer = extJndiInitialContext.list(name);
                    if (enumer.hasMore()) {
                        while (enumer.hasMore()) {
                            NameClassPair pair = enumer.next();
                            extJndiInitialContext.unbind(pair.getName() + "," + name);
                        }
                    } else {
                        extJndiInitialContext.unbind(name);
                    }
                } catch (NotContextException e) {
                    extJndiInitialContext.unbind(name);
                }
                System.out.println("Unbind completed" );
                return;
            } else if (operation.equals(Operation.LIST)) {
                try {
                    NamingEnumeration<NameClassPair> enumer = extJndiInitialContext.list(name);
                    System.out.println("Listing of " + name + " {");
                    while (enumer.hasMore()) {
                        NameClassPair pair = enumer.next();
                        System.out.println(pair.getName());
                    }
                    System.out.println("}\n");
                } catch (NotContextException e) {
                    System.out.println(name + " found, type: " + (extJndiInitialContext.lookup(name)).getClass());
                }
                return;
            }
            // For BIND or REBIND operations also create the Solace JNDI Initial Context
            Hashtable<String, Object> solEnv = new Hashtable<String, Object>();
            solEnv.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
            solEnv.put(InitialContext.PROVIDER_URL, solaceUrl);
            solEnv.put(Context.SECURITY_PRINCIPAL, solaceUsername); // Formatted as user@message-vpn
            solEnv.put(Context.SECURITY_CREDENTIALS, solacePassword);
            solInitialContext = new InitialContext(solEnv);
            // Create the object to bind or rebind through lookup from Solace JNDI
            Reference ref = null;
            if (sourceTopicJndiName != null) {
                // Lookup the topic
                SolTopic topic = (SolTopic) solInitialContext.lookup(sourceTopicJndiName);
                ref = topic.getReference();
                System.out.println("Importing topic " + sourceTopicJndiName + " from Solace JNDI to external JNDI as " + name );
            } else if (sourceQueueJndiName != null) {
                // Lookup the queue
                SolQueue queue = (SolQueue) solInitialContext.lookup(sourceQueueJndiName);
                ref = queue.getReference();
                System.out.println("Importing queue " + sourceQueueJndiName + " from Solace JNDI to external JNDI  as " + name );
            } else if (sourceCfJndiName != null) {
                // Lookup the connection factory
                SolConnectionFactory cf = (SolConnectionFactory) solInitialContext.lookup(sourceCfJndiName);
                ref = cf.getReference();
                System.out.println("Importing connection factory " + sourceCfJndiName + " from Solace JNDI to external JNDI  as " + name );
            }
            // Now bind or rebind the object
            if (operation.equals(Operation.BIND)) {
                extJndiInitialContext.bind(name, ref);
                System.out.println("Bind completed" );
            } else if (operation.equals(Operation.REBIND)) {
                extJndiInitialContext.rebind(name, ref);
                System.out.println("Rebind completed" );
            }
            return;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (extJndiInitialContext != null) {
                try {
                    extJndiInitialContext.close();
                } catch (Exception e) {}
            }
            if (solInitialContext != null) {
                try {
                    solInitialContext.close();
                } catch (Exception e) {}
            }
        }
    }

    public static void main(String[] args) {
        try {
            ExtJndiImport instance = new ExtJndiImport();
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-solaceUrl")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.solaceUrl = args[i];
                } else if (args[i].equals("-solaceUsername")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.solaceUsername = args[i];
                } else if (args[i].equals("-solacePassword")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.solacePassword = args[i];
                } else if (args[i].equals("-jndiUrl")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.extJndiUrl = args[i];
                } else if (args[i].equals("-jndiUsername")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.extJndiUsername = args[i];
                } else if (args[i].equals("-jndiPassword")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.extJndiPassword = args[i];
                } else if (args[i].equals("-operation")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.operation = Operation.valueOf(args[i]);
                } else if (args[i].equals("-cf")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.sourceCfJndiName = args[i];
                } else if (args[i].equals("-topic")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.sourceTopicJndiName = args[i];
                } else if (args[i].equals("-queue")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.sourceQueueJndiName = args[i];
                } else if (args[i].equals("-name")) {
                    i++;
                    if (i >= args.length)
                        instance.printUsage();
                    instance.name = args[i];
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }

            if (instance.extJndiUrl == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiUrl\" parameter");
                return;
            }
            if (instance.extJndiUsername == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiUsername\" parameter");
                return;
            }
            if (instance.extJndiPassword == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiPassword\" parameter");
                return;
            }
            if (instance.operation == null) {
                instance.printUsage();
                System.out.println("Please specify \"-operation\" parameter");
                return;
            }
            if ((instance.operation.equals(Operation.BIND)) || (instance.operation.equals(Operation.REBIND))) {
                if ((instance.solaceUrl == null) && (instance.solaceUsername == null) && (instance.solacePassword == null)) {
                    instance.printUsage();
                    System.out.println("For BIND or REBIND operation please specify all \"-solaceUrl\", \"-solaceUsername\" and \"-solacePassword\" parameters");
                    return;
                }
                if ((instance.sourceCfJndiName == null) && (instance.sourceQueueJndiName == null) && (instance.sourceTopicJndiName == null)) {
                    instance.printUsage();
                    System.out.println("Please specify one of [-cf, -topic, -queue]");
                    return;
                }
            }
            if (instance.name == null) {
                instance.printUsage();
                System.out.println("Please specify \"-name\" parameter");
                return;
            }

            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
