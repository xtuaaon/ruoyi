package com.ruoyi.app.service;

import com.ruoyi.app.impl.IWXTokenService;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.WXUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.ruoyi.framework.web.service.TokenService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WXTokenService extends TokenService implements IWXTokenService
{
    private static final Logger log = LoggerFactory.getLogger(WXTokenService.class);

    // 令牌自定义标识
    @Value("${token.header}")
    private String header;

    // 令牌秘钥
    @Value("${token.secret}")
    private String secret;

    // 令牌有效期（默认30分钟）
    @Value("${token.expireTime}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;

    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private static final Long MILLIS_MINUTE_TWENTY = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    public String createWXToken(WXUser wxUser)
    {
        String tokenKey = IdUtils.fastUUID();
        wxUser.setTokenKey(tokenKey);
        refreshToken(wxUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, tokenKey);
        claims.put(Constants.JWT_USERID, wxUser.getOpenId());
        return createToken(claims);
    }

    public void refreshToken(WXUser wxUser)
    {
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(wxUser.getTokenKey());
        redisCache.setCacheObject(userKey, wxUser, expireTime, TimeUnit.MINUTES);
    }
}
