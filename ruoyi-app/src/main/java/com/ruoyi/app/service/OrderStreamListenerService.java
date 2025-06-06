package com.ruoyi.app.service;


import com.alibaba.fastjson2.JSON;
import com.ruoyi.framework.web.websocket.OrderWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

public class OrderStreamListenerService {
    private static final Logger log = LoggerFactory.getLogger(OrderStreamListenerService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        // 启动多个线程，每个线程监听一种订单类型
        List<String> orderTypes = Arrays.asList("plumbing", "electrical", "carpentry", "painting", "cleaning");

        for (String orderType : orderTypes) {
            new Thread(() -> listenOrderStream(orderType)).start();
        }
    }

    private void listenOrderStream(String orderType) {
        String streamKey = "order_stream:" + orderType;
        String groupName = "order_consumers";
        String consumerName = "websocket_consumer";

        try {
            // 确保消费者组存在
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
            }
            catch (Exception e) {
                // 组可能已存在，忽略异常
                log.warn("创建消费者组异常，可能已存在: {}", e.getMessage());
            }

            String lastId = ">"; // 只消费新消息

            while (true) {
                try {
                    // 从Stream中读取消息
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .read(Consumer.from(groupName, consumerName),
                                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                                    StreamOffset.create(streamKey, ReadOffset.from(lastId)));

                    if (records != null && !records.isEmpty()) {
                        // 处理订单记录
                        List<Map<String, Object>> orderEvents = new ArrayList<>();

                        for (MapRecord<String, Object, Object> record : records) {
                            String recordId = record.getId().getValue();
                            Map<Object, Object> value = record.getValue();

                            // 转换为事件对象
                            Map<String, Object> event = new HashMap<>();
                            value.forEach((k, v) -> event.put(k.toString(), v));
                            event.put("eventId", recordId);
                            event.put("orderType", orderType);
                            orderEvents.add(event);

                            // 确认消息处理完成
                            redisTemplate.opsForStream().acknowledge(streamKey, groupName, recordId);

                            lastId = recordId;
                        }

                        // 将订单事件广播给订阅了该类型的用户
                        String eventsJson = JSON.toJSONString(orderEvents);

                        // 直接使用OrderWebSocketServer的广播方法向特定订单类型频道发送消息
                        OrderWebSocketServer.broadcastToOrderType(orderType, eventsJson);
                    }
                }
                catch (Exception e) {
                    log.error("处理[{}]订单流异常", orderType, e);
                    // 短暂暂停后重试
                    Thread.sleep(1000);
                }
            }
        }
        catch (Exception e) {
            log.error("[{}]订单流监听器异常", orderType, e);
        }
    }
}
