package com.ruoyi.app.mapper;

import com.ruoyi.common.core.domain.entity.WXUser;

public interface WXUserMapper {
    WXUser selectUserByOpenid(String openid);
    WXUser insertUser(WXUser user);
}
