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

import com.google.inject.Injector;
import com.google.inject.Key;
import org.eclipse.paho.client.mqttv3.*;
import org.seedstack.mqtt.MqttRejectedExecutionHandler;
import org.seedstack.seed.SeedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * {@link MqttCallback} used for default reconnection mode.
 *
 * @author thierry.bouvet@mpsa.com
 *
 */
class MqttCallbackAdapter implements MqttCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttCallbackAdapter.class);

    @Inject
    private static Injector injector;

    private Key<MqttCallback> listenerKey;
    private Key<MqttCallback> publisherKey;
    private Key<MqttRejectedExecutionHandler> rejectHandlerKey;
    private IMqttClient mqttClient;
    private MqttClientDefinition clientDefinition;
    private ThreadPoolExecutor pool;

    /**
     * Default constructor.
     *
     * @param mqttClient
     *            mqttClient to reconnect if needed.
     * @param clientDefinition
     *            {@link MqttClientDefinition} for the current mqttClient.
     */
    public MqttCallbackAdapter(IMqttClient mqttClient, MqttClientDefinition clientDefinition) {
        this.mqttClient = mqttClient;
        this.clientDefinition = clientDefinition;
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOGGER.warn("MQTT connection lost for client: {}", mqttClient.getClientId(), cause);
        switch (clientDefinition.getReconnectionMode()) {
        case NONE:
            break;
        case CUSTOM:
            if (publisherKey != null) {
                injector.getInstance(publisherKey).connectionLost(cause);
            }
            if (listenerKey != null) {
                injector.getInstance(listenerKey).connectionLost(cause);
            }
            break;
        case ALWAYS:
        default:
            LOGGER.debug("reconnecting mqttclient {}", mqttClient.getClientId(), cause);
            reconnect();
            break;
        }
    }

    private void reconnect() {
        final Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    connect();
                    timer.cancel();
                } catch (MqttException e) {
                    LOGGER.debug("Can not connect mqttclient {}", mqttClient.getClientId(), e);
                }

            }
        };
        timer.scheduleAtFixedRate(task, clientDefinition.getReconnectionInterval() * 1000,
                clientDefinition.getReconnectionInterval() * 1000);

    }

    private void connect() throws MqttException {
        LOGGER.debug("Trying to connect {}", mqttClient.getClientId());
        MqttClientUtils.connect(mqttClient, clientDefinition);
        LOGGER.info("Client {} is now connected", mqttClient.getClientId());
        if (clientDefinition.getListenerDefinition() != null) {
            MqttClientUtils.subscribe(mqttClient, clientDefinition.getListenerDefinition());
        }
    }

    void start() {
        try {
            connect();
        } catch (MqttException e) {
            LOGGER.debug("Can not connect mqttclient {}", mqttClient.getClientId(), e);
            connectionLost(e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        if (pool != null) {
            try {
                pool.submit(new MqttListenerTask(injector.getInstance(listenerKey), topic, message));
            } catch (Exception e) {
                if (this.rejectHandlerKey == null) {
                    throw SeedException.wrap(e, MqttErrorCodes.LISTENER_ERROR);
                }
                injector.getInstance(this.rejectHandlerKey).reject(topic, message);
            }
        } else {
            injector.getInstance(listenerKey).messageArrived(topic, message);
        }

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        if (publisherKey != null) {
            injector.getInstance(publisherKey).deliveryComplete(token);
        }
    }

    public void setListenerKey(Key<MqttCallback> key) {
        this.listenerKey = key;
    }

    public void setPublisherKey(Key<MqttCallback> publisherKey) {
        this.publisherKey = publisherKey;
    }

    public void setPool(ThreadPoolExecutor pool) {
        this.pool = pool;
    }

    public void setRejectHandlerKey(Key<MqttRejectedExecutionHandler> rejectHandlerKey) {
        this.rejectHandlerKey = rejectHandlerKey;
    }

}
