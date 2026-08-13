package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

import com.ruwei.domain.empty.Auditlog;
import com.ruwei.manager.AuditlogMapper;
import com.ruwei.service.AuditlogService;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【auditlog(审核日志表)】的数据库操作Service实现
* @createDate 2026-08-13 16:00:43
*/
@Service
public class AuditlogServiceImpl extends ServiceImpl<AuditlogMapper, Auditlog>
    implements AuditlogService {

}




