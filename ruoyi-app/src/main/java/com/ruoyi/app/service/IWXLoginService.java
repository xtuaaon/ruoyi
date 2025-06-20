package com.ruoyi.app.service;

import com.ruoyi.common.core.domain.entity.WXUser;

import java.util.Map;

public interface IWXLoginService {
    Map<String, Object> Login(String code);
    Map<String, Object> register(WXUser user);
}
