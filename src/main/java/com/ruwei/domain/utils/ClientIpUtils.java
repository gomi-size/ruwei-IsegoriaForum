package com.ruwei.domain.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析工具。
 *
 * <p>与 {@code RateLimitAspect#getClientIp} 逻辑保持一致：优先取
 * {@code X-Forwarded-For} 首值（经代理/网关场景），缺省回退 {@code remoteAddr}。</p>
 *
 * @author Administrator
 */
public final class ClientIpUtils {

    /**
     * 工具类禁止实例化。
     */
    private ClientIpUtils() {
    }

    /**
     * 解析客户端 IP。
     *
     * @param request 当前请求
     * @return 客户端 IP；请求为 null 时返回 {@code null}
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return StrUtil.subBefore(forwardedFor, ',', false).trim();
        }
        return request.getRemoteAddr();
    }
}