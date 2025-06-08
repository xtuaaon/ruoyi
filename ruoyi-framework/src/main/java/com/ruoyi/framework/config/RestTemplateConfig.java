package com.ruoyi.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        // 基本配置
        RestTemplate restTemplate = new RestTemplate();

        // 设置请求工厂（可选）
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 连接超时时间（毫秒）
        factory.setReadTimeout(5000);    // 读取超时时间（毫秒）
        restTemplate.setRequestFactory(factory);

        return restTemplate;
    }
}
