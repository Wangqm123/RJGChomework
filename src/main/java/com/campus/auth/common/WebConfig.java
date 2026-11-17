package com.campus.auth.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（课程演示用）：
 * 允许任何来源的页面调用本服务接口（浏览器同源策略限制）。
 *
 * 答辩说明：浏览器出于安全只允许页面请求同源接口，
 * 我们的前端页面和后端服务部署在不同端口/机器上，属于跨源，
 * 所以服务端要显式声明"允许跨域访问"。
 * 生产环境应改为白名单模式（只允许指定的前端域名）。
 */
@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
