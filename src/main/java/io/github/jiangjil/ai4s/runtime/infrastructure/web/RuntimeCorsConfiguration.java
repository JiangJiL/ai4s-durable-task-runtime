package io.github.jiangjil.ai4s.runtime.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发阶段允许 ai-management-web 直接读取 Runtime Trace API。
 * 生产环境应将 allowed-origins 改为 ai-management 前端域名，或改由受认证 BFF 代理。
 */
@Configuration
public class RuntimeCorsConfiguration implements WebMvcConfigurer {
    private final String allowedOrigins;
    public RuntimeCorsConfiguration(@Value("${runtime.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/runtime/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
