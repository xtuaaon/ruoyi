package com.ruoyi.app.service.impl;

import com.ruoyi.app.mapper.WXUserMapper;
import com.ruoyi.app.service.IWXUserService;
import com.ruoyi.common.core.domain.entity.WXUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WXUserService implements IWXUserService {
    @Autowired
    private WXUserMapper WXMapper;

    public WXUser findUserByOpenid(String openid){
        return WXMapper.selectUserByOpenid(openid);
    }

    public void insertUser(WXUser user){
        WXMapper.insertUser(user);
    }
}
