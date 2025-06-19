package com.ruoyi.app.service;

import com.ruoyi.common.core.domain.entity.WXMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MasterRedisService {

    private static final Logger log = LoggerFactory.getLogger(MasterRedisService.class);

    @Autowired
    public StringRedisTemplate redisTemplate;

    public List<WXMaster> getOnlineList(double latitude, double longitude, int distance){
        List<WXMaster> result = new ArrayList<>();

        // Redis中存储地理位置的key
        String MASTER_GEO_KEY = "master:locations";

        try {
            // 1. 使用Redis GEO命令查询指定范围内的师傅
            Circle circle = new Circle(new Point(longitude, latitude), new Distance(distance, Metrics.KILOMETERS));
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance() // 包含距离信息
                    .includeCoordinates() // 包含坐标信息
                    .sortAscending(); // 按距离升序排序

            GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
                    redisTemplate.opsForGeo().radius(MASTER_GEO_KEY, circle, args);

            if (geoResults != null) {
                // 2. 遍历查询结果
                for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
                    String masterId = geoResult.getContent().getName(); // 获取师傅ID (openId)
                    Point position = geoResult.getContent().getPoint(); // 获取位置坐标
                    Distance dist = geoResult.getDistance(); // 获取距离

                    // 3. 获取师傅的状态信息
                    String statusKey = "master:status:" + masterId;
                    Map<Object, Object> statusInfo = redisTemplate.opsForHash().entries(statusKey);

                    // 如果状态信息不存在或师傅离线，则跳过
                    if (statusInfo == null || statusInfo.isEmpty() ||
                            "OFFLINE".equals(statusInfo.get("status"))) {
                        continue;
                    }

                    // 4. 从数据库获取完整的师傅信息
                    // 注意：这里应该根据你的实际情况调用相应的Service或Repository
                    WXMaster master = masterService.getByOpenId(masterId);

                    if (master != null) {
                        // 5. 更新师傅的位置和距离信息
                        master.setLatitude(position.getY());
                        master.setLongitude(position.getX());
                        master.setOnlineStatus((String) statusInfo.get("status"));

                        // 6. 添加到结果列表
                        result.add(master);
                    }
                }
            }
        } catch (Exception e) {
            // 处理异常
            log.error("Error fetching online masters within geo fence", e);
        }
        return result;
    }

    /**
     * 新增师傅位置信息
     * @param masterId 师傅ID
     * @param longitude 经度
     * @param latitude 纬度
     * @param masterInfo 师傅详细信息Map
     */
    public void addMasterLocation(String masterId, double longitude, double latitude, Map<String, String> masterInfo) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();

        // 1. 添加到GEO数据结构
        Point point = new Point(longitude, latitude);
        redisTemplate.opsForGeo().add("master:locations", point, masterId);

        // 2. 存储师傅详细信息
        String infoKey = "master:info:" + masterId;
        redisTemplate.opsForHash().putAll(infoKey, masterInfo);

        // 3. 根据状态添加到对应的集合
        String status = masterInfo.getOrDefault("status", "available");
        redisTemplate.opsForSet().add("master:status:" + status, masterId);

        // 4. 记录添加时间
        masterInfo.put("lastUpdateTime", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().put(infoKey, "lastUpdateTime", masterInfo.get("lastUpdateTime"));

        // 可选：设置过期时间，如果需要自动清理长时间不活跃的师傅
        // redisTemplate.expire(infoKey, 24, TimeUnit.HOURS);
    }



}
