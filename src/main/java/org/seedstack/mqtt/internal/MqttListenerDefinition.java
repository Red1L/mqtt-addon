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

import org.eclipse.paho.client.mqttv3.MqttCallback;

/**
 * Defined all topics/qos to listen.
 * 
 * @author thierry.bouvet@mpsa.com
 *
 */
class MqttListenerDefinition {

    private String[] topicFilter;
    private String className;
    private int[] qos;
    private Class<? extends MqttCallback> listenerClass;

    public MqttListenerDefinition(Class<? extends MqttCallback> mqttListenerClass, String className,
            String[] topicFilter, int[] qos) {
        this.topicFilter = topicFilter;
        this.className = className;
        this.qos = qos;
        this.listenerClass = mqttListenerClass;
    }

    public String[] getTopicFilter() {
        return topicFilter;
    }

    public String getClassName() {
        return className;
    }

    public int[] getQos() {
        return qos;
    }

    public Class<? extends MqttCallback> getListenerClass() {
        return listenerClass;
    }

}
