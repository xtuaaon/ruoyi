package com.ruoyi.app.service;

import com.ruoyi.app.impl.IWXLoginService;
import com.ruoyi.app.impl.IWXTokenService;
import com.ruoyi.app.impl.IWXUserService;
import com.ruoyi.common.core.domain.entity.WXUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WXLoginService implements IWXLoginService {
    @Value("${wechat.appId}")
    private String appId;

    @Value("${wechat.appSecret}")
    private String appSecret;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private IWXUserService _WXUserService;

    @Autowired
    private IWXTokenService _WXTokenService;


    @Override
    public Map<String, Object> Login(String code){
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId +
                "&secret=" + appSecret +
                "&js_code=" + code +
                "&grant_type=authorization_code";

        try {
            // 发送HTTP GET请求到微信服务器
            Map<String, Object> wxResponse = restTemplate.getForObject(url, Map.class);

            if (wxResponse != null && wxResponse.containsKey("openid") && wxResponse.containsKey("session_key")) {
                String openid = (String) wxResponse.get("openid");

                // 查找用户是否已注册
                WXUser user = _WXUserService.findUserByOpenid(openid);

                Map<String, Object> result = new HashMap<>();

                if (user == null)
                {
                    // 用户未注册
                    result.put("isRegistered", false);
                    result.put("openid", openid);
                }
                else
                {
                    // 用户已注册，生成token
                    String token = _WXTokenService.createWXToken(user);
                    result.put("token", token);
                    result.put("isRegistered", true);
                    result.put("message", "登录成功");
                }
                return result;
            }
            else
            {
                // 处理微信API返回的错误
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "微信API返回错误: " + wxResponse);
                return errorResult;
            }
        }
        catch (Exception e)
        {
            // 处理请求异常
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "请求微信API时发生错误: " + e.getMessage());
            return errorResult;
        }
    }


    @Override
    public Map<String, Object> register(WXUser user){
        _WXUserService.insertUser(user);
        Map<String, Object> result = new HashMap<>();
        String token = _WXTokenService.createWXToken(user);
        result.put("token", token);
        result.put("isRegistered", true);
        result.put("message", "登录成功");
        return result;
    }

}
