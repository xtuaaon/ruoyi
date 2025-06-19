package com.ruoyi.app.service;

import com.ruoyi.common.core.domain.entity.WXMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WXMasterService {

    private static final Logger log = LoggerFactory.getLogger(WXMasterService.class);

    public List<WXMaster> getOnlineList(double latitude, double longitude, int distance){
        List<WXMaster> result = new ArrayList<>();

        return result;

    }


}
