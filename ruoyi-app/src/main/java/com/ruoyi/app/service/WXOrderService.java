package com.ruoyi.app.service;

import com.ruoyi.app.dto.inputdto.wxorder.newOrderInputDto;
import com.ruoyi.app.mapper.WXOrderMapper;
import com.ruoyi.common.core.domain.entity.WXOrder;
import com.ruoyi.common.enums.OrderStatusEnum;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

public class WXOrderService {

    private static final Logger log = LoggerFactory.getLogger(WXOrderService.class);

    @Autowired
    public WXOrderMapper wxOrderMapper;

    @Autowired
    public OrderRedisService orderPublishService;

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
            orderPublishService.createOrderZSet(order);

            // 4. 发送订单创建消息到Redis Stream
            orderPublishService.createOrderStream(order);

            // 5. 构建并返回结果
            return buildSuccessResult(order.getId());
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage(), e);
            throw new ServiceException("订单创建过程中发生错误: " + e.getMessage());
        }
    }

    /**
     * 分页获取待处理订单，使用时间戳作为游标
     * @param lastTimestamp 上次查询的最小时间戳
     * @param limit 每页数量
     * @return 订单列表响应
     */
    public Map<String, Object> getPendingOrders(long lastTimestamp,int orderType, int limit) {


        return new OrderListResponse(orders, hasMore, newLastTimestamp);
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
