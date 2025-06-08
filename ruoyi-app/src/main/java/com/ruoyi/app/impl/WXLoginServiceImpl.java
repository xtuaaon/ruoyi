package com.ruoyi.app.impl;

import com.ruoyi.common.core.domain.entity.WXUser;

import java.util.Map;

public interface WXLoginServiceImpl {
    Map<String, Object> Login(String code);
    Map<String, Object> register(WXUser user);
}
