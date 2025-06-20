package com.ruoyi.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.ruoyi.common.constant.RedisKeyConstants;
import com.ruoyi.common.core.domain.entity.WXOrder;
import com.ruoyi.common.enums.OrderTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OrderRedisService {

    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    private static final Set<OrderTypeEnum> ORDER_TYPES = EnumSet.of(
            OrderTypeEnum.Plumbing,
            OrderTypeEnum.Cement,
            OrderTypeEnum.Carpentry,
            OrderTypeEnum.Internet
    );

    /**
     * 将订单信息缓存到Redis
     */
    public void createOrderZSet(WXOrder order) {
        String orderKey = RedisKeyConstants.ORDER_HASH_KEY + order.getId();
        // 使用Hash结构存储订单信息
        Map<String, Object> orderMap = BeanUtil.beanToMap(order);
        redisTemplate.opsForHash().putAll(orderKey, orderMap);
        // 设置过期时间
        redisTemplate.expire(orderKey, 24, TimeUnit.HOURS);

        String setKey;
        int orderType = order.getType();
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(orderType);
        if (ORDER_TYPES.contains(orderTypeEnum)) {
            setKey = RedisKeyConstants.PENDING_ORDER_ZSET + orderType;
        }
        else {
            setKey = RedisKeyConstants.PENDING_ORDER_ZSET +"general";
        }

        // 将订单ID添加到待接单集合
        redisTemplate.opsForZSet().add(setKey, order.getId(),System.currentTimeMillis());

        redisTemplate.opsForZSet().add(RedisKeyConstants.PENDING_ORDER_ZSET + "global", order.getId(),System.currentTimeMillis());
    }

    public boolean acceptOrderZSet(String orderId, int orderType, String userId, String creator) {
        String typeSpecificZSetKey;
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(orderType);
        if (ORDER_TYPES.contains(orderTypeEnum)) {
            typeSpecificZSetKey = RedisKeyConstants.PENDING_ORDER_ZSET + orderType;
        } else {
            typeSpecificZSetKey = RedisKeyConstants.PENDING_ORDER_ZSET + "general";
        }

        // 使用Lua脚本保证原子性操作
        String luaScript =
                "local orderKey = KEYS[1] " +
                        "local pendingOrdersGlobal = KEYS[2] " +
                        "local pendingOrdersType = KEYS[3] " +
                        "local assignedKey = KEYS[4] " +
                        "if redis.call('exists', orderKey) == 1 and redis.call('hget', orderKey, 'status') == 'PENDING' then " +
                        "  redis.call('hset', orderKey, 'status', 'ASSIGNED', 'assignedTo', ARGV[1], 'assignedTime', ARGV[2]) " +
                        "  redis.call('zrem', pendingOrdersGlobal, ARGV[3]) " +  // 使用zrem替代srem
                        "  redis.call('zrem', pendingOrdersType, ARGV[3]) " +    // 使用zrem替代srem
                        "  redis.call('sadd', assignedKey, ARGV[3]) " +
                        "  -- 设置合理的过期时间，例如24小时 " +
                        "  redis.call('expire', orderKey, 86400) " +
                        "  redis.call('expire', assignedKey, 86400) " +
                        "  return 1 " +
                        "else " +
                        "  return 0 " +
                        "end";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);

        Long result = redisTemplate.execute(
                redisScript,
                Arrays.asList(
                        RedisKeyConstants.ORDER_HASH_KEY + orderId,              // KEYS[1] - 订单键
                        RedisKeyConstants.PENDING_ORDER_ZSET + "global",         // KEYS[2] - 全局待接单有序集合键
                        typeSpecificZSetKey,                                     // KEYS[3] - 特定类型待接单有序集合键
                        RedisKeyConstants.ACCEPTED_ORDER_PREFIX + userId         // KEYS[4] - 用户已抢订单集合键
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                orderId
        );

        return result == 1;
    }

    public void createOrderStream(WXOrder order) {
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

    public void acceptOrderStream(String orderId, int orderType, String userId, String creator) {
        OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(orderType);
        // 构建订单数据
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", orderId);
        orderMap.put("type", orderType);
        orderMap.put("event", "接受订单");
        orderMap.put("createTime", DateUtil.format(new Date(),"yyyy-MM-dd HH:mm:ss"));

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

    public Map<String, Object> getPendingOrders(long lastTimestamp,int orderType, int limit){
        String zsetKey;

        // 根据订单类型确定使用哪个ZSet键
        if (orderType > 0) {
            OrderTypeEnum orderTypeEnum = OrderTypeEnum.fromCode(orderType);
            if (ORDER_TYPES.contains(orderTypeEnum)) {
                zsetKey = RedisKeyConstants.PENDING_ORDER_ZSET + orderType;
            } else {
                zsetKey = RedisKeyConstants.PENDING_ORDER_ZSET + "general";
            }
        } else {
            // 如果不指定类型，使用全局订单集合
            zsetKey = RedisKeyConstants.PENDING_ORDER_ZSET + "global";
        }

        Set<ZSetOperations.TypedTuple<Object>> results;

        // 首次加载：直接获取最新的limit条记录
        if (lastTimestamp == 0) {
            // 使用zrevrange获取最新的limit+1条记录
            results = redisTemplate.opsForZSet().reverseRangeWithScores(
                    zsetKey,
                    0,           // 从索引0开始
                    limit        // 获取limit+1条记录
            );
        }
        else {
            // 非首次加载：获取比lastTimestamp更早的订单(降序排列)
            results = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                    zsetKey,
                    0,                // 最小分数
                    lastTimestamp - 1, // 确保不包含上次的最后一条记录
                    0,                // 偏移量
                    limit + 1         // 多取一条用于判断是否还有更多
            );
        }

        // 处理结果
        List<String> orderIds = new ArrayList<>();
        long newLastTimestamp = 0;

        int count = 0;
        for (ZSetOperations.TypedTuple<Object> tuple : results) {
            if (count < limit) {
                String orderId = tuple.toString();
                if (orderId != null) {
                    orderIds.add(orderId);
                    if (tuple.getScore() != null) {
                        newLastTimestamp = tuple.getScore().longValue();
                    }
                }
            }
            count++;
        }

        // 使用Pipeline批量获取订单详情
        List<WXOrder> orders = fetchOrderDetails(orderIds);

        // 判断是否还有更多数据
        boolean hasMore = count > limit;

        // 构建返回结果
        Map<String, Object> response = new HashMap<>();
        response.put("orders", orders);
        response.put("hasMore", hasMore);
        response.put("lastTimestamp", newLastTimestamp);

        return  response;

    }

    private List<WXOrder> fetchOrderDetails(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<WXOrder> orders = new ArrayList<>(orderIds.size());

        // 使用Redis Pipeline批量获取订单详情
        List<Object> pipelineResults = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();

                for (String orderId : orderIds) {
                    String hashKey = RedisKeyConstants.ORDER_HASH_KEY + orderId;
                    operations.opsForHash().entries(hashKey);
                }
                return operations.exec();
            }
        });

        // 处理结果
        if (pipelineResults != null) {
            for (int i = 0; i < pipelineResults.size(); i++) {
                if (i < orderIds.size()) {
                    Map<Object, Object> map = (Map<Object, Object>) pipelineResults.get(i);
                    if (map != null && !map.isEmpty()) {
                        WXOrder order = convertMapToOrder(map, orderIds.get(i));
                        orders.add(order);
                    }
                }
            }
        }

        return orders;

    }

    private WXOrder convertMapToOrder(Map<Object, Object> map, String orderId) {
        WXOrder order = new WXOrder();
        order.setId(orderId);
        return order;
    }



}
