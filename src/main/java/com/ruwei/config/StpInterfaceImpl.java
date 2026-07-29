package com.ruwei.config;

import cn.dev33.satoken.stp.StpInterface;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.Enum.AdminEnum;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 数据接口：根据 user 表的 admin 标志位，向框架提供当前登录用户的 角色 与 权限。
 * StpUtil.checkRole / @SaCheckRole / checkPermission / @SaCheckPermission 最终都会回调这里，
 * 因此「谁是什么角色、有哪些权限」完全由 user.admin 这个字段驱动。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserService userService;

    /** 角色：管理员返回 [admin]，普通用户返回 [user] */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userService.getById(toLong(loginId));
        if (user == null) {
            return List.of();
        }
        int admin = user.getAdmin() != null ? user.getAdmin() : 0;
        return AdminEnum.isAdmin(admin) ? List.of("admin") : List.of("user");
    }

    /** 权限：管理员拥有全部权限；普通用户仅能发布自己的文章（为将来 @SaCheckPermission 预留） */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = userService.getById(toLong(loginId));
        if (user == null) {
            return List.of();
        }
        int admin = user.getAdmin() != null ? user.getAdmin() : 0;
        if (AdminEnum.isAdmin(admin)) {
            return List.of(
                    "user:view:all",
                    "user:update",
                    "user:status",
                    "article:add",
                    "article:update",
                    "article:delete"
            );
        }
        return List.of("article:add");
    }

    private Long toLong(Object loginId) {
        return Long.valueOf(loginId.toString());
    }
}
