package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 用户修改密码的请求类（已登录场景）。
 *
 * <p>用于替换原 {@code /user/editPassword} 的裸参数形式 {@code (Long id, String password)}：
 * 该形式与项目内其余写接口「统一 JSON 请求体 + DTO」的契约不一致，
 * 且 {@code id} 语义含糊（既可能是内部雪花主键，也可能是对外编码 userId），
 * 参数名 {@code Password} 的大写首字母在部分前端序列化实现下还会被静默忽略。</p>
 *
 * <p><b>注意</b>：{@code id} 一律传<b>内部主键 id</b>，且必须与当前登录态一致 ——
 * 本接口只允许用户修改自己的密码，不承担管理员代改职责
 * （管理员代改走 {@code /admin/user/forgetPassword}）。</p>
 *
 * @author Administrator
 */
@Data
public class EditPasswordDTO {

    /**
     * 目标用户内部主键 id（必须等于当前登录用户的 id，否则拒绝）
     */
    private Long id;

    /**
     * 新密码，长度 8~12 位且不能全为数字
     */
    private String password;

    /**
     * 确认新密码，需与 {@link #password} 一致
     */
    private String checkPassword;
}
