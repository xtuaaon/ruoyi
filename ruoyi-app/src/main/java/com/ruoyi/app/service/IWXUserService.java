package com.ruoyi.app.service;

import com.ruoyi.common.core.domain.entity.WXUser;

public interface IWXUserService {
    WXUser findUserByOpenid(String openid);
    void insertUser(WXUser user);
}
