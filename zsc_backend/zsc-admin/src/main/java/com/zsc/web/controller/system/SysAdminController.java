package com.zsc.web.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zsc.common.annotation.Log;
import com.zsc.common.core.controller.BaseController;
import com.zsc.common.core.domain.AjaxResult;
import com.zsc.common.enums.BusinessType;
import com.zsc.common.core.domain.entity.SysUser;
import com.zsc.module.domain.entity.BizBill;
import com.zsc.module.domain.vo.ReviewerWorkloadVo;
import com.zsc.module.domain.vo.UserAmountVo;
import com.zsc.module.service.BizBillService;
import com.zsc.common.utils.SecurityUtils;
import com.zsc.module.common.exception.ServiceException;
import com.zsc.module.common.tools.UserAccountUtils;
import com.zsc.module.service.BizRegisterRequestService;
import com.zsc.module.service.EmailService;
import com.zsc.system.mapper.SysUserMapper;
import com.zsc.system.mapper.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统管理员控制器
 */
@RestController
@RequestMapping("/api/admin")
public class SysAdminController extends BaseController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private com.zsc.system.mapper.SysRoleMapper roleMapper;

    @Autowired
    private BizBillService billService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BizRegisterRequestService registerRequestService;


    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping("/stats")
    public AjaxResult stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", userMapper.countUser());
        data.put("userCount", userMapper.countUserByRoleKey("user"));
        data.put("reviewerCount", userMapper.countUserByRoleKey("reviewer"));
        data.put("adminCount", userMapper.countUserByRoleKey("admin_user"));
        data.put("totalBills", billService.count());
        data.put("pendingBills", billService.count(
            new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "1")));
        data.put("approvedBills", billService.count(
            new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "2")));
        data.put("rejectedBills", billService.count(
            new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "3")));
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/users")
    public AjaxResult users(@RequestParam(required = false) String userName,
                              @RequestParam(required = false) String roleKey,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String excludeRoleKey,
                              @RequestParam(defaultValue = "1") int currentPage,
                              @RequestParam(defaultValue = "10") int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysUser> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(currentPage, pageSize);
        List<SysUser> users = userMapper.selectUserWithRoles(page, userName, roleKey, status, excludeRoleKey);
        Map<String, Object> data = new HashMap<>();
        data.put("list", users);
        data.put("total", page.getTotal());
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/roles")
    public AjaxResult roles() {
        return success(roleMapper.selectRoleAll());
    }

    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping("/register-requests")
    public AjaxResult listRequests() {
        return success(registerRequestService.listPending());
    }

    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @Log(title = "注册申请", businessType = BusinessType.UPDATE)
    @PostMapping("/register-requests/{id}/approve")
    public AjaxResult approveRequest(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        return success(registerRequestService.approve(id, comment));
    }

    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @Log(title = "注册申请", businessType = BusinessType.UPDATE)
    @PostMapping("/register-requests/{id}/reject")
    public AjaxResult rejectRequest(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        registerRequestService.reject(id, comment);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping("/reviewer-workload")
    public AjaxResult reviewerWorkload() {
        List<BizBill> bills = billService.list(
            new LambdaQueryWrapper<BizBill>()
                .isNotNull(BizBill::getAuditBy)
                .in(BizBill::getStatus, "2", "3"));
        Map<String, ReviewerWorkloadVo> map = new HashMap<>();
        for (BizBill b : bills) {
            String name = b.getAuditBy();
            ReviewerWorkloadVo vo = map.computeIfAbsent(name,
                k -> new ReviewerWorkloadVo(k, 0, 0, 0));
            vo.setTotalCount(vo.getTotalCount() + 1);
            if ("2".equals(b.getStatus())) {
                vo.setApprovedCount(vo.getApprovedCount() + 1);
            } else {
                vo.setRejectedCount(vo.getRejectedCount() + 1);
            }
        }
        List<ReviewerWorkloadVo> result = map.values().stream()
            .sorted(Comparator.comparingLong(ReviewerWorkloadVo::getTotalCount).reversed())
            .collect(Collectors.toList());
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping("/user-amount-summary")
    public AjaxResult userAmountSummary() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysUser> bigPage =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 9999);
        List<SysUser> normalUsers = userMapper.selectUserWithRoles(bigPage, null, "user", null, null);
        java.util.Set<String> userNames = normalUsers.stream()
            .map(SysUser::getUserName)
            .collect(Collectors.toSet());
        if (userNames.isEmpty()) {
            return success(java.util.Collections.emptyList());
        }
        List<BizBill> approvedBills = billService.list(
            new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, "2")
                .in(BizBill::getCreateBy, userNames));
        Map<String, Long> amountMap = new HashMap<>();
        for (BizBill b : approvedBills) {
            String name = b.getCreateBy();
            long amt = b.getAmount() != null ? b.getAmount().longValue() : 0L;
            amountMap.merge(name, amt, Long::sum);
        }
        List<UserAmountVo> result = amountMap.entrySet().stream()
            .map(e -> new UserAmountVo(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(UserAmountVo::getTotalAmount).reversed())
            .collect(Collectors.toList());
        return success(result);
    }

    /**
     * 超管直接创建管理员账号（输入邮箱 → 生成随机用户名密码 → 分配 admin_user 角色）
     */
    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @PostMapping("/create-admin")
    public AjaxResult createAdmin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return error("邮箱不能为空");
        }
        email = email.trim();

        // 邮箱唯一性校验
        SysUser exist = userMapper.checkEmailUnique(email);
        if (exist != null) {
            return error("该邮箱已被使用");
        }

        String rawPassword = UserAccountUtils.generatePassword();
        String username = UserAccountUtils.generateUsername(email, "admin", userMapper);

        SysUser user = new SysUser();
        user.setUserName(username);
        user.setNickName(username);
        user.setEmail(email);
        user.setPassword(SecurityUtils.encryptPassword(rawPassword));
        user.setStatus("0");
        user.setPwdUpdateDate(new Date());
        userMapper.insertUser(user);

        // 分配 admin_user 角色
        Long adminRoleId = getRoleIdByKey("admin_user");
        com.zsc.system.domain.SysUserRole ur = new com.zsc.system.domain.SysUserRole();
        ur.setUserId(user.getUserId());
        ur.setRoleId(adminRoleId);
        userRoleMapper.batchUserRole(java.util.Collections.singletonList(ur));

        emailService.sendCredentials(email, username, rawPassword, "系统管理员");

        Map<String, Object> result = new HashMap<>();
        result.put("userName", username);
        result.put("password", rawPassword);
        result.put("email", email);
        return success(result);
    }

    @Autowired
    private com.zsc.system.service.ISysConfigService configService;

    /**
     * 获取操作日志自动导出间隔（分钟，0=关闭）
     */
    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping("/operlog-export-interval")
    public AjaxResult getExportInterval() {
        String val = configService.selectConfigByKey("sys.operlog.autoExportInterval");
        return success(Integer.parseInt(val != null ? val : "0"));
    }

    /**
     * 设置操作日志自动导出间隔（分钟，0=关闭）
     */
    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @PutMapping("/operlog-export-interval")
    public AjaxResult setExportInterval(@RequestBody Map<String, Integer> body) {
        int interval = body.getOrDefault("interval", 0);
        if (interval < 0 || interval > 1440) {
            return error("间隔必须为 0-1440 分钟");
        }
        // 通过 key 查 configId，避免 updateConfig 时 NPE
        com.zsc.system.domain.SysConfig query = new com.zsc.system.domain.SysConfig();
        query.setConfigKey("sys.operlog.autoExportInterval");
        java.util.List<com.zsc.system.domain.SysConfig> list = configService.selectConfigList(null, query);
        if (list.isEmpty()) return error("配置项不存在");
        com.zsc.system.domain.SysConfig cfg = list.get(0);
        cfg.setConfigValue(String.valueOf(interval));
        configService.updateConfig(cfg);
        return success();
    }

    private Long getRoleIdByKey(String roleKey) {
        com.zsc.common.core.domain.entity.SysRole role = roleMapper.checkRoleKeyUnique(roleKey);
        if (role == null) {
            throw new ServiceException("系统错误：角色[" + roleKey + "]不存在");
        }
        return role.getRoleId();
    }
}
