package com.ruoyi.app.controller;

import com.ruoyi.app.service.IWXLoginService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.WXUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WXLoginController {
    @Autowired
    private IWXLoginService wxloginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;


    @PostMapping("/wxlogin")
    public AjaxResult wxlogin(@RequestBody String code)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        Map<String, Object> data = wxloginService.Login(code);
        ajax.put("data", data);
        return ajax;
    }


    @GetMapping("wxgetInfo")
    public AjaxResult wxgetInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        return ajax;
    }

    @PostMapping("/wxregister")
    public AjaxResult wxregister(@RequestBody WXUser user)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        Map<String, Object> data = wxloginService.register(user);
        ajax.put("data", data);
        return ajax;
    }

}
