package com.ruwei.controller.manager;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.SensitiveWordAddDTO;
import com.ruwei.domain.empty.SensitiveWord;
import com.ruwei.service.SensitiveWordService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 敏感词管理端接口。
 *
 * <p>提供敏感词的查询、新增（支持单条与批量）、删除能力，需 {@code admin} 角色
 * （通过 {@code @SaCheckRole("admin")} 校验，角色由 {@code user.admin} 字段推导）。
 * 所有写操作成功后内部均会触发内存词库热刷新。</p>
 *
 * @see SensitiveWordService
 */
@RestController
@RequestMapping("/admin/sensitive-words")
@SaCheckRole("admin")
public class SensitiveWordController {

    @Resource
    private SensitiveWordService sensitiveWordService;



/**
 * 获取全部敏感词列表。
 *
 * @return 敏感词实体列表
 */
@GetMapping
public BaseResponse<List<SensitiveWord>> list() {
    return ResultUtils.success(sensitiveWordService.listAll());
}

/**
 * 新增敏感词。请求体既可以是单个对象，也可以是对象数组（批量一次性传多组）。
 * <pre>
 * 单个：  {"word":"代开发票","category":2,"action":2}
 * 批量：  [{"word":"代开发票","action":2},{"word":"傻子","action":1}]
 * </pre>
 * 内部统一转换为 {@link SensitiveWordAddDTO} 列表后调用批量写入。
 *
 * @param body 单个对象或对象数组
 * @return 成功写入的条数
 */
@PostMapping
public BaseResponse<Integer> add(@RequestBody Object body) {
    int count = sensitiveWordService.addBatch(body);
    ThrowUtils.throwIf(count == 0, ErrorCode.PARAMS_ERROR, "没有有效的敏感词");
    return ResultUtils.success(count);
}



/**
 * 根据主键删除敏感词。
 *
 * @param id 敏感词对应的主键 id
 * @return 删除结果提示
 */
@DeleteMapping("/{id}")
public BaseResponse<String> delete(@PathVariable Long id) {
    ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "id不能为空");
    boolean result = sensitiveWordService.deleteWord(id);
    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
    return ResultUtils.success("删除成功");
}
}
