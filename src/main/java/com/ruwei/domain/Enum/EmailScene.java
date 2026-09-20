package com.ruwei.domain.Enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 邮箱验证码使用场景枚举。
 *
 * <p>用于隔离不同业务场景的验证码：同一邮箱的「注册码」不能拿去「重置密码」，
 * 不同场景的验证码在 Redis 中互不可见。</p>
 *
 * <p><b>注意</b>：{@link #code} 参与 Redis key 拼接
 * （{@code auth:email:code:{scene}:{email}}），一经上线不可随意变更，
 * 否则会造成历史验证码全部失效。</p>
 *
 * @author Administrator
 */
@Getter
@AllArgsConstructor
public enum EmailScene {

    /** 注册：邮箱未注册时建号 */
    REGISTER("register", "注册"),

    /** 登录：邮箱已注册时建立登录态 */
    LOGIN("login", "登录"),

    /** 重置密码：凭邮箱验证码重置密码（替代原无校验的 forgetPassword 流程） */
    RESET_PASSWORD("resetPwd", "重置密码"),

    /** 绑定邮箱：预留，本期未开放接口 */
    BIND_EMAIL("bindEmail", "绑定邮箱");

    /** 场景编码（参与 Redis key 拼接） */
    private final String code;

    /** 场景描述 */
    private final String desc;

    /**
     * 根据场景编码获取枚举实例。
     *
     * @param code 场景编码
     * @return 对应的枚举值，未匹配返回 null
     */
    public static EmailScene getByCode(String code) {
        for (EmailScene scene : values()) {
            if (scene.code.equals(code)) {
                return scene;
            }
        }
        return null;
    }

    /**
     * 判断给定的场景编码是否有效。
     *
     * @param code 场景编码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return getByCode(code) != null;
    }
}
