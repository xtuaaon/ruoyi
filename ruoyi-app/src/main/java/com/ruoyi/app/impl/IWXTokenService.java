package com.ruoyi.app.impl;

import com.ruoyi.common.core.domain.entity.WXUser;

public interface IWXTokenService {
    String createWXToken(WXUser wxUser);
    void refreshToken(WXUser wxUser);
}
