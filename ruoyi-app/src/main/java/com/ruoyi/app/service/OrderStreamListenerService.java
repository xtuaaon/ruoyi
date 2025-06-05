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
                redisTemplate.opsForStream().createGroup(streamKey, groupName);
            }
            catch (Exception e) {
                // 组可能已存在，忽略异常
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
                        List<Map<String, Object>> orderList = new ArrayList<>();

                        for (MapRecord<String, Object, Object> record : records) {
                            String recordId = record.getId().getValue();
                            Map<Object, Object> value = record.getValue();

                            // 转换为订单对象
                            Map<String, Object> order = new HashMap<>();
                            value.forEach((k, v) -> order.put(k.toString(), v));
                            orderList.add(order);

                            // 确认消息处理完成
                            redisTemplate.opsForStream().acknowledge(streamKey, groupName, recordId);

                            lastId = recordId;
                        }

                        // 将订单列表广播给对应类型的WebSocket客户端
                        String orderListJson = JSON.toJSONString(orderList);
                        OrderWebSocketServer.broadcastToOrderType(orderType, orderListJson);
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
