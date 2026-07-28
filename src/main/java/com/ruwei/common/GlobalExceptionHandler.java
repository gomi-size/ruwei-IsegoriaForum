package com.ruwei.common;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一将异常转换为 {@link BaseResponse}，保证前后端错误契约一致。
 * 处理优先级由 Spring 按异常类型 specificity 自动排序（越具体越先命中）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：ThrowUtils / BusinessException 主动抛出的可预期错误
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e) {
        // 返回异常自带的 code 与 message，契约与 ResultUtils 一致
        log.error("出现错误{}：", e.getMessage());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * Sa-Token 未登录异常：后续接入 @SaCheckLogin 等鉴权注解时统一返回未登录
     * 注意：未登录/登录态失效是正常业务状态，不是系统故障，禁止用 ERROR 记录（否则日志刷屏 + 误报警）
     */
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> handleNotLoginException(NotLoginException e) {
        log.debug("未登录拦截（{}）：", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    /**
     * 兜底：未预期的系统异常，避免把堆栈直接抛给前端（安全 + 契约统一）
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleException(Exception e) {
        log.error("系统未处理异常，请联系管理员：", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
    }
}
