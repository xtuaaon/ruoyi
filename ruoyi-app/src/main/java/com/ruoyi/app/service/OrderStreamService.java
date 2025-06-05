package com.ruoyi.app.service;

import com.ruoyi.common.constant.RedisKeyConstants;
import com.ruoyi.common.core.domain.entity.WXOrder;
import com.ruoyi.common.enums.OrderTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

public class OrderStreamService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Set<OrderTypeEnum> ORDER_TYPES = EnumSet.of(
            OrderTypeEnum.Plumbing,
            OrderTypeEnum.Cement,
            OrderTypeEnum.Carpentry,
            OrderTypeEnum.Internet
    );

    public void createOrder(WXOrder order) {
        int orderType = order.getType();
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(orderType);

        // 构建订单数据
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", order.getId());
        orderMap.put("description", order.getDescription());
        orderMap.put("type", orderType);
        orderMap.put("event", "创建订单");
        orderMap.put("createTime", order.getCreateTime());

        // 确定流key
        String streamKey;
        if (ORDER_TYPES.contains(orderTypeEnum)) {
            streamKey = RedisKeyConstants.ORDER_STREAM_KEY + orderType;
        }
        else {
            streamKey = RedisKeyConstants.ORDER_STREAM_KEY+"general";
        }

        // 发布到特定类型流
        redisTemplate.opsForStream().add(streamKey, orderMap);

        // 同时发布到全局流
        redisTemplate.opsForStream().add(RedisKeyConstants.ORDER_STREAM_KEY+"global", orderMap);
    }
}
