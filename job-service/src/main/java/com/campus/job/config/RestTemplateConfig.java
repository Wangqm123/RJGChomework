package com.campus.job.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 跨服务调用用的 HTTP 客户端配置。
 *
 * 为什么必须设置超时：
 * 分布式系统中对方服务可能没启动、网络不通或响应很慢，
 * 不设超时会导致本服务线程被长时间挂起，最终把整个服务拖垮。
 * 这里连接 3 秒、读取 5 秒，超时后由 AuthClient 转成友好的 500 提示。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
