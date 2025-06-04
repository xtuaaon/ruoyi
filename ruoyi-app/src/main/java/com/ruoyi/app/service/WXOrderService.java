package com.ruoyi.app.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.ruoyi.app.dto.inputdto.wxorder.acceptOrderInputDto;
import com.ruoyi.app.dto.inputdto.wxorder.newOrderInputDto;
import com.ruoyi.app.mapper.WXOrderMapper;
import com.ruoyi.common.constant.RedisKeyConstants;
import com.ruoyi.common.core.domain.entity.WXOrder;
import com.ruoyi.common.enums.OrderStatusEnum;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class WXOrderService {

    private static final Logger log = LoggerFactory.getLogger(WXOrderService.class);

    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public WXOrderMapper wxOrderMapper;

    /**
     * 创建订单
     */
    public Map<String, Object> newOrder(newOrderInputDto inputDto) {
        try {
            // 1. 创建订单对象并设置基本信息
            WXOrder order = buildOrderFromInput(inputDto);

            // 2. 保存订单到数据库
            int rows = wxOrderMapper.insertOrder(order);
            if (rows <= 0) {
                throw new ServiceException("订单创建失败");
            }

            // 3. 将待接单订单信息添加到Redis
            cacheOrderInRedis(order);

            // 4. 发送订单创建消息到Redis Stream
            sendCreateOrderMessage(order);

            // 5. 构建并返回结果
            return buildSuccessResult(order.getId());
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage(), e);
            throw new ServiceException("订单创建过程中发生错误: " + e.getMessage());
        }
    }

    /**
     * 获取订单列表
     */
    public List<String> getOrderList(){
        List<String> result = new ArrayList<>();
        // 从Redis Set中获取所有待接单的订单ID
        Set<Object> pendingOrderIds = redisTemplate.opsForSet().members(RedisKeyConstants.PENDING_ORDER_SET);
        if (pendingOrderIds == null || pendingOrderIds.isEmpty()) {
            return result;
        }
        // 将Object类型转换为String类型
        for (Object orderId : pendingOrderIds) {
            result.add(orderId.toString());
        }
        return result;
    }

    /**
     * 接受订单
     */
    public boolean acceptOrder(acceptOrderInputDto inputDto){
        String orderId = inputDto.getOrderId();
        int type = inputDto.getType();
        String userId = inputDto.getRecipient();
        String creator = inputDto.getCreator();
        // 使用Lua脚本保证原子性操作
        String luaScript =
                "local orderKey = KEYS[1] " +
                        "local pendingOrdersKey = KEYS[2]"+
                        "local assignedKey = KEYS[3] " +
                        "if redis.call('hget', orderKey, 'status') == 'PENDING' then " +
                        "  redis.call('hset', orderKey, 'status', 'ASSIGNED', 'assignedTo', ARGV[1], 'assignedTime', ARGV[2]) " +
                        "  redis.call('sadd', assignedKey, ARGV[3]) " +
                        "  redis.call('srem', pendingOrdersKey, ARGV[3]) "+
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
                        orderId,                                              // KEYS[1] - 订单键
                        RedisKeyConstants.PENDING_ORDER_SET,                  // KEYS[2] - 待接单集合键
                        RedisKeyConstants.ACCEPTED_ORDER_PREFIX + userId      // KEYS[3] - 用户已抢订单集合键
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                orderId
        );

        if (result == 1) {
            sendAcceptOrderMessage(orderId,type,userId,creator);
            return true;
        }
        else {
            return false;
        }
    }

    private WXOrder buildOrderFromInput(newOrderInputDto inputDto) {
        WXOrder order = new WXOrder();
        // 使用雪花ID作为订单ID
        order.setId(IdUtils.fastUUID());
        order.setType(inputDto.getType());
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setDescription(inputDto.getDescription());
        order.setCreator(inputDto.getUserId());
        order.setCreateTime(new Date());
        // 可以添加更多字段设置
        return order;
    }

    /**
     * 将订单信息缓存到Redis
     */
    private void cacheOrderInRedis(WXOrder order) {
        String orderKey = order.getId();
        // 使用Hash结构存储订单信息
        Map<String, Object> orderMap = BeanUtil.beanToMap(order);
        redisTemplate.opsForHash().putAll(orderKey, orderMap);
        // 设置过期时间
        redisTemplate.expire(orderKey, 24, TimeUnit.HOURS);

        // 将订单ID添加到待接单集合
        redisTemplate.opsForSet().add(RedisKeyConstants.PENDING_ORDER_SET, order.getId());
    }

    /**
     * 发送创建订单消息到Redis Stream
     */
    private void sendCreateOrderMessage(WXOrder order) {
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("orderId", order.getId());
        messageMap.put("type", String.valueOf(order.getType()));
        messageMap.put("status", String.valueOf(order.getStatus()));
        messageMap.put("information", "创建订单");
        messageMap.put("createTime", DateUtil.format(order.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
        messageMap.put("creator", order.getCreator());

        // 发送到Redis Stream
        StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
        streamOps.add(RedisKeyConstants.ORDER_STREAM_KEY, messageMap);
    }

    /**
     * 发送接受订单消息到Redis Stream
     */
    private void sendAcceptOrderMessage(String orderId,int type, String recipient ,String creator) {
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("orderId", orderId);
        messageMap.put("type", String.valueOf(type));
        messageMap.put("status", String.valueOf(OrderStatusEnum.ACCEPTED.getCode()));
        messageMap.put("information", "接受订单");
        messageMap.put("createTime", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
        messageMap.put("creator", creator);

        // 发送到Redis Stream
        StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
        streamOps.add(RedisKeyConstants.ORDER_STREAM_KEY, messageMap);
    }

    /**
     * 构建成功结果
     */
    private Map<String, Object> buildSuccessResult(String orderId) {
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("createTime", DateUtils.getTime());
        result.put("status", "success");
        return result;
    }
}
