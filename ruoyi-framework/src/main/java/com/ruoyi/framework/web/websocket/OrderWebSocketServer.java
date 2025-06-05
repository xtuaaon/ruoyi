package com.ruoyi.framework.web.websocket;

import com.ruoyi.common.enums.OrderTypeEnum;
import com.ruoyi.framework.config.CustomSpringConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ServerEndpoint(value = "/websocket/orders/{orderType}", configurator = CustomSpringConfigurator.class)
public class OrderWebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(OrderWebSocketServer.class);

    /**
     * 按订单类型分组存储会话
     */
    private static final Map<String, Set<Session>> ORDER_TYPE_SESSIONS = new ConcurrentHashMap<>();

    /**
     * 记录当前在线连接数
     */
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    private Session session;
    private String orderType;

    @OnOpen
    public void onOpen(Session session, @PathParam("orderType") String orderType) {
        this.session = session;
        this.orderType = orderType;

        // 确保该订单类型的Set已初始化
        ORDER_TYPE_SESSIONS.computeIfAbsent(orderType, k -> ConcurrentHashMap.newKeySet());

        // 添加会话到对应类型的集合
        ORDER_TYPE_SESSIONS.get(orderType).add(session);
        ONLINE_COUNT.incrementAndGet();

        log.info("新工人连接到[{}]订单频道，当前在线总人数:{}", orderType, ONLINE_COUNT.get());
    }

    @OnClose
    public void onClose() {
        if (orderType != null && ORDER_TYPE_SESSIONS.containsKey(orderType)) {
            ORDER_TYPE_SESSIONS.get(orderType).remove(session);
            ONLINE_COUNT.decrementAndGet();
            log.info("工人断开[{}]订单频道连接，当前在线总人数:{}", orderType, ONLINE_COUNT.get());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到来自[{}]频道的消息:{}", orderType, message);
        // 处理抢单请求等
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("[{}]频道发生错误", orderType, error);
    }

    /**
     * 向特定类型的订单频道广播消息
     */
    public static void broadcastToOrderType(String orderType, String message) {
        Set<Session> sessions = ORDER_TYPE_SESSIONS.get(orderType);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("向[{}]频道广播消息失败", orderType, e);
                }
            }
        }
    }
}
