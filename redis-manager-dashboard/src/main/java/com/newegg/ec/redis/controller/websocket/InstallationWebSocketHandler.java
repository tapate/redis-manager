package com.newegg.ec.redis.controller.websocket;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.sockjs.SockJsTransportFailureException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by gl49 on 2018/4/22.
 */
@Component
public class InstallationWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstallationWebSocketHandler.class);

    private static Map<String, BlockingDeque<String>> CLUSTER_MESSAGE_MAP = new ConcurrentHashMap<>();

    private static Map<WebSocketSession, String> WEB_SOCKET_CLUSTER_MAP = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        try {
            Map<String, Object> attributes = webSocketSession.getAttributes();
            System.err.println(attributes);
            webSocketSession.sendMessage(new TextMessage("Start installation"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) {
        try {
            String clusterName = webSocketMessage.getPayload().toString();
            createLogQueueIfNotExist(clusterName, webSocketSession);
            BlockingDeque<String> logQueue;
            while (true) {
                logQueue = getLogQueue(clusterName);
                if (logQueue == null) {
                    break;
                }
                if (logQueue.size() == 0) {
                    Thread.sleep(1000);
                }
                String message = logQueue.poll();
                message = "hahaha0";
                if (!Strings.isNullOrEmpty(message)) {
                    try {
                        webSocketSession.sendMessage(new TextMessage(message));
                    } catch (SockJsTransportFailureException e) {
                        // ignore
                        e.printStackTrace();
                    } catch (IOException e) {
                        // ignore
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) {
        try {
            if (webSocketSession.isOpen()) {
                webSocketSession.close();
            }
            removeLogMap(webSocketSession);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        removeLogMap(webSocketSession);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public static void appendLog(String clusterName, String message) {
        BlockingDeque<String> blockingDeque = getLogQueue(clusterName);
        if (null != blockingDeque) {
            blockingDeque.add(message);
        }
    }

    public static void removeLogMap(WebSocketSession webSocketSession) {
        String clusterName = WEB_SOCKET_CLUSTER_MAP.get(webSocketSession);
        WEB_SOCKET_CLUSTER_MAP.remove(webSocketSession);
        if (CLUSTER_MESSAGE_MAP != null && !Strings.isNullOrEmpty(clusterName)) {
            CLUSTER_MESSAGE_MAP.remove(clusterName);
        }
    }

    private static BlockingDeque<String> createLogQueueIfNotExist(String clusterName, WebSocketSession webSocketSession) {
        CLUSTER_MESSAGE_MAP.putIfAbsent(clusterName, new LinkedBlockingDeque<>());
        WEB_SOCKET_CLUSTER_MAP.putIfAbsent(webSocketSession, clusterName);
        return CLUSTER_MESSAGE_MAP.get(clusterName);
    }

    private static BlockingDeque<String> getLogQueue(String clusterName) {
        return CLUSTER_MESSAGE_MAP.get(clusterName);
    }
}
