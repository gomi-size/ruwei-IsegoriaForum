package com.ruwei.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 Sa-Token 拦截器，使 @SaCheckLogin / @SaCheckRole / @SaCheckPermission 等注解真正生效。
 * sa-token-spring-boot3-starter 不会自动注册路径映射，官方 Quickstart 要求手动注册（否则注解不生效）。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                // 放行 Swagger / Knife4j 文档与静态资源，避免被鉴权拦截
                .excludePathPatterns(
                        "/doc.html",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/v2/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }
}
