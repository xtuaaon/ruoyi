package com.ruoyi.app.service;

import com.ruoyi.app.mapper.WXUserMapper;
import com.ruoyi.common.core.domain.entity.WXUser;
import org.springframework.beans.factory.annotation.Autowired;

public class WXUserService {
    @Autowired
    private WXUserMapper WXMapper;

    public WXUser findUserByOpenid(String openid){
        return WXMapper.selectUserByOpenid(openid);
    }

    public void insertUser(WXUser user){
        WXMapper.insertUser(user);
    }
}
