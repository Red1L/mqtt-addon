/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/**
 *
 */
package org.seedstack.mqtt.internal;

import java.lang.annotation.Annotation;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.javatuples.Pair;
import org.seedstack.mqtt.MqttRejectedExecutionHandler;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * Module to initialize {@link MqttClient} and {@link MqttCallback}.
 *
 * @author thierry.bouvet@mpsa.com
 *
 */
class MqttModule extends AbstractModule {

    private ConcurrentHashMap<Pair<String, String>, MqttClientDefinition> mqttClientDefinitions;
    private ConcurrentHashMap<Pair<String, String>, IMqttClient> mqttClients;

    public MqttModule(ConcurrentHashMap<Pair<String, String>, IMqttClient> mqttClients,
                      ConcurrentHashMap<Pair<String, String>, MqttClientDefinition> mqttClientDefinitions) {
        this.mqttClientDefinitions = mqttClientDefinitions;
        this.mqttClients = mqttClients;
    }

    @Override
    protected void configure() {
        requestStaticInjection(MqttCallbackAdapter.class);
        for (Entry<Pair<String, String>, MqttClientDefinition> entry : mqttClientDefinitions.entrySet()) {
            MqttClientDefinition clientDefinition = entry.getValue();
            Pair<String, String> clientKey = entry.getKey();
            IMqttClient mqttClient = mqttClients.get(clientKey);
            bind(IMqttClient.class).annotatedWith(clientQualifier(clientKey)).toInstance(mqttClient);
            MqttCallbackAdapter callbackAdapter = new MqttCallbackAdapter(mqttClient, clientDefinition);
            mqttClient.setCallback(callbackAdapter);

            MqttPublisherDefinition publisherDefinition = clientDefinition.getPublisherDefinition();
            if (publisherDefinition != null) {
                registerPublisHandler(callbackAdapter, publisherDefinition);
            }
            MqttListenerDefinition listenerDefinition = clientDefinition.getListenerDefinition();
            if (listenerDefinition != null) {
                registerListener(mqttClient, callbackAdapter, clientDefinition);
            }
        }

    }


    static Annotation clientQualifier(Pair<String, String> clientKey) {
        if (clientKey.getValue1() == null) {
            return Names.named(clientKey.getValue0());
        }
        return Names.named(clientKey.getValue0() + "." + clientKey.getValue1());
    }

    private void registerPublisHandler(MqttCallbackAdapter callbackAdapter,
                                       MqttPublisherDefinition publisherDefinition) {
        String className = publisherDefinition.getClassName();
        Class<? extends MqttCallback> clazz = publisherDefinition.getPublisherClass();
        bind(MqttCallback.class).annotatedWith(Names.named(className)).to(clazz);
        callbackAdapter.setPublisherKey(Key.get(MqttCallback.class, Names.named(className)));
    }

    private void registerListener(IMqttClient mqttClient, MqttCallbackAdapter callbackAdapter,
                                  MqttClientDefinition clientDefinition) {
        MqttListenerDefinition listenerDefinition = clientDefinition.getListenerDefinition();
        String className = listenerDefinition.getClassName();
        Class<? extends MqttCallback> clazz = listenerDefinition.getListenerClass();
        bind(MqttCallback.class).annotatedWith(Names.named(className)).to(clazz);
        callbackAdapter.setListenerKey(Key.get(MqttCallback.class, Names.named(className)));
        MqttPoolDefinition poolDefinition = clientDefinition.getPoolDefinition();
        callbackAdapter.setPool(poolDefinition.getThreadPoolExecutor());
        if (poolDefinition.getRejectHandlerClass() != null) {
            String rejectName = poolDefinition.getRejectHandlerName();
            Class<? extends MqttRejectedExecutionHandler> rejectClass = poolDefinition.getRejectHandlerClass();
            bind(MqttRejectedExecutionHandler.class).annotatedWith(Names.named(rejectName)).to(rejectClass);
            callbackAdapter.setRejectHandlerKey(Key.get(MqttRejectedExecutionHandler.class, Names.named(rejectName)));
        }
    }

}
