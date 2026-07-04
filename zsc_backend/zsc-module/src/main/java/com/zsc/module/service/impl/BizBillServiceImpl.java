package com.zsc.module.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zsc.common.utils.SecurityUtils;
import com.zsc.module.common.exception.ServiceException;
import com.zsc.module.common.pagination.PageResult;
import com.zsc.module.domain.dto.BizBillDto;
import com.zsc.module.domain.dto.BizBillReviewDto;
import com.zsc.module.domain.dto.query.BizBillQueryDto;
import com.zsc.module.domain.entity.BizAuditLog;
import com.zsc.module.domain.entity.BizBill;
import com.zsc.module.domain.entity.BizBillFile;
import com.zsc.module.domain.entity.BizCategory;
import com.zsc.module.domain.vo.BizAuditLogVo;
import com.zsc.module.domain.vo.BizBillDetailVo;
import com.zsc.module.domain.vo.BizBillFileVo;
import com.zsc.module.domain.vo.BizBillVo;
import com.zsc.module.domain.vo.ReviewerStatsVo;
import com.zsc.module.domain.vo.ReviewTrendVo;
import com.zsc.module.domain.vo.TrendItemVo;
import com.zsc.module.mapper.BizAuditLogMapper;
import com.zsc.module.mapper.BizBillFileMapper;
import com.zsc.module.mapper.BizBillMapper;
import com.zsc.module.mapper.BizCategoryMapper;
import com.zsc.module.service.BizBillService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 票据表 服务实现类
 */
@Service
public class BizBillServiceImpl extends ServiceImpl<BizBillMapper, BizBill> implements BizBillService {

    @Autowired
    private BizBillFileMapper fileMapper;

    @Autowired
    private BizAuditLogMapper auditLogMapper;

    @Autowired
    private BizCategoryMapper categoryMapper;

    @Autowired
    private com.zsc.system.mapper.SysUserMapper sysUserMapper;

    /**
     * 管理员禁止执行用户专属操作
     */
    private void checkNotAdmin() {
        if (SecurityUtils.hasPermi("biz:bill:review")) {
            throw new ServiceException("管理员不能执行用户操作！");
        }
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBill(BizBillDto dto) {
        checkNotAdmin();
        BizBill bill = new BizBill();
        BeanUtils.copyProperties(dto, bill);

        bill.setCreateBy(SecurityUtils.getUsername());
        bill.setCreateTime(new Date());

        // 先保存草稿（用临时编号占位），再根据状态替换为正式编号
        bill.setBillNo("DRAFT-" + System.currentTimeMillis());

        if (!this.save(bill)) {
            throw new ServiceException("系统错误，票据保存失败！");
        }

        if ("1".equals(dto.getStatus())) {
            bill.setBillNo(generateBillNo(bill.getId()));
            this.updateById(bill);
        }

        // 保存附件记录
        saveAttachments(bill.getId(), dto.getAttachment());
    }

    // ==================== 查询列表 ====================

    @Override
    public PageResult<BizBillVo> queryBills(BizBillQueryDto dto) {
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();

        // 普通用户只查自己的票据
        if (!SecurityUtils.hasPermi("biz:bill:review")) {
            wrapper.eq(BizBill::getCreateBy, SecurityUtils.getUsername());
        } else {
            // 防御性过滤：审核员只能看到活跃用户（未停用/未删除）的票据
            List<String> activeUsers = sysUserMapper.selectActiveUserNames();
            if (!activeUsers.isEmpty()) {
                wrapper.in(BizBill::getCreateBy, activeUsers);
            }
        }

        // 条件过滤
        if (StringUtils.isNotBlank(dto.getKeywords())) {
            wrapper.and(w -> w
                .like(BizBill::getTitle, dto.getKeywords())
                .or()
                .like(BizBill::getBillNo, dto.getKeywords())
            );
        }
        wrapper.eq(dto.getCategoryId() != null, BizBill::getCategoryId, dto.getCategoryId())
               .eq(StringUtils.isNotBlank(dto.getStatus()), BizBill::getStatus, dto.getStatus())
               .like(StringUtils.isNotBlank(dto.getAuditBy()), BizBill::getAuditBy, dto.getAuditBy())
               .like(StringUtils.isNotBlank(dto.getCreateBy()), BizBill::getCreateBy, dto.getCreateBy())
               .ge(dto.getMinAmount() != null, BizBill::getAmount, dto.getMinAmount())
               .le(dto.getMaxAmount() != null, BizBill::getAmount, dto.getMaxAmount())
               .ge(StringUtils.isNotBlank(dto.getStartTime()), BizBill::getCreateTime, dto.getStartTime())
               .le(StringUtils.isNotBlank(dto.getEndTime()), BizBill::getCreateTime, dto.getEndTime());

        // 审核日期过滤：按审核时间当天范围筛选
        if (StringUtils.isNotBlank(dto.getAuditDate())) {
            String auditDateStart = dto.getAuditDate() + " 00:00:00";
            String auditDateEnd = dto.getAuditDate() + " 23:59:59";
            wrapper.ge(BizBill::getAuditTime, auditDateStart)
                   .le(BizBill::getAuditTime, auditDateEnd);
        }

        wrapper.last(buildOrderSql(dto.getSortField(), dto.getSortOrder()));

        Page<BizBill> page = this.page(dto.convertToPage(), wrapper);

        // 批量查类别名称
        Map<Long, String> categoryNameMap = buildCategoryNameMap(page.getRecords());

        // Entity → VO
        List<BizBillVo> voList = page.getRecords().stream().map(bill -> {
            BizBillVo vo = new BizBillVo();
            BeanUtils.copyProperties(bill, vo);
            if (bill.getCategoryId() != null) {
                vo.setCategoryName(categoryNameMap.get(bill.getCategoryId()));
            }
            return vo;
        }).collect(Collectors.toList());

        PageResult result = PageResult.fromPage(page);
        result.setList(voList);
        return result;
    }

    // ==================== 详情 ====================

    @Override
    public BizBillDetailVo getBillDetail(Long id) {
        BizBill bill = this.getById(id);
        if (bill == null) {
            throw new ServiceException("票据不存在！");
        }

        // 非管理员只能查看自己的票据
        if (!SecurityUtils.hasPermi("biz:bill:review")
                && !SecurityUtils.getUsername().equals(bill.getCreateBy())) {
            throw new ServiceException("只能查看自己的票据！");
        }

        BizBillDetailVo vo = new BizBillDetailVo();
        BeanUtils.copyProperties(bill, vo);

        // 查类别名称
        if (bill.getCategoryId() != null) {
            BizCategory category = categoryMapper.selectById(bill.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
            }
        }

        // 查附件列表
        List<BizBillFile> files = fileMapper.selectList(
            new LambdaQueryWrapper<BizBillFile>()
                .eq(BizBillFile::getBillId, id)
                .orderByAsc(BizBillFile::getSortOrder)
        );
        vo.setFiles(files.stream().map(f -> {
            BizBillFileVo fvo = new BizBillFileVo();
            BeanUtils.copyProperties(f, fvo);
            return fvo;
        }).collect(Collectors.toList()));

        // 查审核历史
        List<BizAuditLog> logs = auditLogMapper.selectList(
            new LambdaQueryWrapper<BizAuditLog>()
                .eq(BizAuditLog::getBillId, id)
                .orderByDesc(BizAuditLog::getAuditTime)
        );
        vo.setAuditLogs(logs.stream().map(l -> {
            BizAuditLogVo lvo = new BizAuditLogVo();
            BeanUtils.copyProperties(l, lvo);
            return lvo;
        }).collect(Collectors.toList()));

        return vo;
    }

    // ==================== 编辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBill(BizBillDto dto) {
        checkNotAdmin();
        BizBill bill = this.getById(dto.getId());
        if (bill == null) {
            throw new ServiceException("票据不存在！");
        }

        // 仅草稿或已退回可编辑
        if (!List.of("0", "3").contains(bill.getStatus())) {
            throw new ServiceException("只能编辑草稿或已退回状态的票据！");
        }

        // 校验数据归属
        if (!SecurityUtils.getUsername().equals(bill.getCreateBy())) {
            throw new ServiceException("只能编辑自己的票据！");
        }

        // 保留字段不被 null 覆盖
        if (StringUtils.isNotBlank(dto.getAttachment())) {
            bill.setAttachment(dto.getAttachment());
        }

        bill.setTitle(dto.getTitle());
        bill.setCategoryId(dto.getCategoryId());
        bill.setAmount(dto.getAmount());
        bill.setDescription(dto.getDescription());
        bill.setUpdateBy(SecurityUtils.getUsername());
        bill.setUpdateTime(new Date());

        if (!this.updateById(bill)) {
            throw new ServiceException("系统错误，票据更新失败！");
        }

        // 仅当传入了新附件时才替换旧附件（含磁盘文件）
        if (StringUtils.isNotBlank(dto.getAttachment())) {
            deleteFilesAndRecords(new LambdaQueryWrapper<BizBillFile>()
                .eq(BizBillFile::getBillId, bill.getId()));
            saveAttachments(bill.getId(), dto.getAttachment());
        }
    }

    // ==================== 提交审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitBill(Long id) {
        checkNotAdmin();
        BizBill bill = this.getById(id);
        if (bill == null) {
            throw new ServiceException("票据不存在！");
        }

        // 仅草稿或已退回可提交
        if (!List.of("0", "3").contains(bill.getStatus())) {
            throw new ServiceException("只能提交草稿或已退回状态的票据！");
        }

        // 校验数据归属
        if (!SecurityUtils.getUsername().equals(bill.getCreateBy())) {
            throw new ServiceException("只能提交自己的票据！");
        }

        // 草稿有临时编号的，替换为正式编号（用主键ID保证并发唯一）
        if (StringUtils.isBlank(bill.getBillNo()) || bill.getBillNo().startsWith("DRAFT-")) {
            bill.setBillNo(generateBillNo(bill.getId()));
        }

        bill.setStatus("1");
        bill.setUpdateBy(SecurityUtils.getUsername());
        bill.setUpdateTime(new Date());

        if (!this.updateById(bill)) {
            throw new ServiceException("系统错误，票据提交失败！");
        }
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBill(Long id) {
        checkNotAdmin();
        BizBill bill = this.getById(id);
        if (bill == null) {
            throw new ServiceException("票据不存在！");
        }

        // 仅草稿可删除
        if (!"0".equals(bill.getStatus())) {
            throw new ServiceException("只能删除草稿状态的票据！");
        }

        // 校验数据归属
        if (!SecurityUtils.getUsername().equals(bill.getCreateBy())) {
            throw new ServiceException("只能删除自己的票据！");
        }

        // 先删附件（含磁盘文件），再删票据
        deleteFilesAndRecords(new LambdaQueryWrapper<BizBillFile>()
            .eq(BizBillFile::getBillId, id));
        this.removeById(id);
    }

    // ==================== 审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewBill(BizBillReviewDto dto) {
        // 校验 action 值合法性
        if (!List.of("1", "2").contains(dto.getAction())) {
            throw new ServiceException("无效的审核结果，action 必须为 1（通过）或 2（退回）！");
        }

        BizBill bill = this.getById(dto.getBillId());
        if (bill == null) {
            throw new ServiceException("票据不存在！");
        }

        // 仅已提交状态可审核
        if (!"1".equals(bill.getStatus())) {
            throw new ServiceException("只能审核已提交状态的票据！");
        }

        // 更新票据审核信息
        bill.setStatus(dto.getAction().equals("1") ? "2" : "3");
        bill.setAuditBy(SecurityUtils.getUsername());
        bill.setAuditTime(new Date());
        bill.setAuditComment(dto.getComment());
        bill.setUpdateBy(SecurityUtils.getUsername());
        bill.setUpdateTime(new Date());

        if (!this.updateById(bill)) {
            throw new ServiceException("系统错误，审核失败！");
        }

        // 记录审核历史
        BizAuditLog log = BizAuditLog.builder()
            .billId(dto.getBillId())
            .action(dto.getAction())
            .comment(dto.getComment())
            .auditBy(SecurityUtils.getUsername())
            .auditTime(new Date())
            .build();
        auditLogMapper.insert(log);
    }

    // ==================== 附件 ====================

    /**
     * 删除附件记录及磁盘文件
     */
    private void deleteFilesAndRecords(LambdaQueryWrapper<BizBillFile> wrapper) {
        List<BizBillFile> files = fileMapper.selectList(wrapper);
        for (BizBillFile f : files) {
            String diskPath = com.zsc.common.config.RuoYiConfig.getProfile()
                + f.getFilePath().replace(com.zsc.common.constant.Constants.RESOURCE_PREFIX, "");
            new java.io.File(diskPath).delete();
        }
        fileMapper.delete(wrapper);
    }

    /**
     * 解析附件路径字符串，创建附件记录
     * @param billId 票据ID
     * @param attachment 逗号分隔的附件路径
     */
    private void saveAttachments(Long billId, String attachment) {
        if (StringUtils.isBlank(attachment)) {
            return;
        }

        String[] paths = attachment.split(",");
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i].trim();
            if (StringUtils.isBlank(path)) {
                continue;
            }

            BizBillFile file = BizBillFile.builder()
                .billId(billId)
                .fileName(path.substring(path.lastIndexOf("/") + 1))
                .filePath(path)
                .sortOrder(i)
                .createBy(SecurityUtils.getUsername())
                .createTime(new Date())
                .build();
            fileMapper.insert(file);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 批量查询类别ID对应的类别名称
     */
    private String buildOrderSql(String sortField, String sortOrder) {
        if (sortField == null || sortField.isEmpty()) return "ORDER BY create_time DESC";
        String col = switch (sortField) {
            case "amount" -> "amount";
            case "createTime" -> "create_time";
            case "auditTime" -> "audit_time";
            default -> "create_time";
        };
        String dir = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return "ORDER BY " + col + " " + dir;
    }

    private Map<Long, String> buildCategoryNameMap(List<BizBill> bills) {
        List<Long> categoryIds = bills.stream()
            .map(BizBill::getCategoryId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

        if (categoryIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        List<BizCategory> categories = categoryMapper.selectBatchIds(categoryIds);
        return categories.stream()
            .collect(Collectors.toMap(BizCategory::getCategoryId, BizCategory::getCategoryName, (a, b) -> a, java.util.HashMap::new));
    }

    /**
     * 生成票据编号: BILL-yyyyMMdd-{billId}
     * 使用票据主键ID保证并发安全，避免批量提交时编号重复
     */
    private String generateBillNo(Long billId) {
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return String.format("BILL-%s-%d", dateStr, billId);
    }

    @Override
    public List<TrendItemVo> getMonthlyTrend() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;

        Calendar start = Calendar.getInstance();
        start.set(year, month - 1, 1, 0, 0, 0);
        String startStr = new SimpleDateFormat("yyyy-MM-dd").format(start.getTime());
        String endStr = new SimpleDateFormat("yyyy-MM-dd").format(now.getTime());

        LambdaQueryWrapper<BizBill> trendWrapper = new LambdaQueryWrapper<BizBill>()
            .ge(BizBill::getUpdateTime, startStr)
            .le(BizBill::getUpdateTime, endStr)
            .ne(BizBill::getStatus, "0");
        if (SecurityUtils.hasPermi("*:*:*")) {
            // 超管看全量
        } else if (SecurityUtils.hasPermi("biz:bill:review")) {
            trendWrapper.eq(BizBill::getAuditBy, SecurityUtils.getUsername());
        } else {
            trendWrapper.eq(BizBill::getCreateBy, SecurityUtils.getUsername());
        }
        List<BizBill> bills = this.list(trendWrapper);

        Map<String, Long> weekMap = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            weekMap.put("第" + i + "周", 0L);
        }

        for (BizBill bill : bills) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(bill.getUpdateTime());
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            String weekLabel = "第" + ((dayOfMonth - 1) / 7 + 1) + "周";
            weekMap.merge(weekLabel, 1L, Long::sum);
        }

        List<TrendItemVo> result = new ArrayList<>();
        weekMap.forEach((label, count) -> result.add(new TrendItemVo(label, count)));
        return result;
    }

    @Override
    public List<TrendItemVo> getCategoryAmountSummary() {
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getStatus, "2");
        if (SecurityUtils.hasPermi("*:*:*")) {
            // 超管看全量
        } else if (SecurityUtils.hasPermi("biz:bill:review")) {
            wrapper.eq(BizBill::getAuditBy, SecurityUtils.getUsername());
        } else {
            wrapper.eq(BizBill::getCreateBy, SecurityUtils.getUsername());
        }
        List<BizBill> bills = this.list(wrapper);

        Map<Long, Long> amountMap = new LinkedHashMap<>();
        Map<Long, String> nameMap = new LinkedHashMap<>();

        for (BizBill bill : bills) {
            Long cid = bill.getCategoryId();
            if (cid != null) {
                amountMap.merge(cid, bill.getAmount() != null ? bill.getAmount().longValue() : 0L, Long::sum);
                if (!nameMap.containsKey(cid)) {
                    BizCategory cat = categoryMapper.selectById(cid);
                    nameMap.put(cid, cat != null ? cat.getCategoryName() : "未知");
                }
            }
        }

        return amountMap.entrySet().stream()
            .map(e -> new TrendItemVo(nameMap.getOrDefault(e.getKey(), "未知"), e.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public ReviewerStatsVo getReviewerStats() {
        String username = SecurityUtils.getUsername();
        ReviewerStatsVo vo = new ReviewerStatsVo();

        // 待审核数量（仅统计活跃用户的票据）
        LambdaQueryWrapper<BizBill> pendingWrapper = new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getStatus, "1");
        List<String> activeUsers = sysUserMapper.selectActiveUserNames();
        if (!activeUsers.isEmpty()) {
            pendingWrapper.in(BizBill::getCreateBy, activeUsers);
        }
        vo.setPendingCount((int) this.count(pendingWrapper));

        // 今日通过/退回
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        int todayApproved = (int) this.count(
            new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getAuditBy, username)
                .eq(BizBill::getStatus, "2")
                .ge(BizBill::getAuditTime, today));
        int todayRejected = (int) this.count(
            new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getAuditBy, username)
                .eq(BizBill::getStatus, "3")
                .ge(BizBill::getAuditTime, today));
        vo.setTodayApproved(todayApproved);
        vo.setTodayRejected(todayRejected);

        // 通过率
        int totalReviewed = todayApproved + todayRejected;
        vo.setApprovalRate(totalReviewed > 0
            ? Math.round(todayApproved * 100.0 / totalReviewed) : 0);

        // 积压预警: 超过3天未审核（仅统计活跃用户的票据）
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -3);
        LambdaQueryWrapper<BizBill> staleWrapper = new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getStatus, "1")
            .lt(BizBill::getCreateTime, cal.getTime());
        if (!activeUsers.isEmpty()) {
            staleWrapper.in(BizBill::getCreateBy, activeUsers);
        }
        vo.setStalePending((int) this.count(staleWrapper));

        // 最近审核记录
        List<BizBill> recentBills = this.list(
            new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getAuditBy, username)
                .in(BizBill::getStatus, "2", "3")
                .orderByDesc(BizBill::getAuditTime)
                .last("LIMIT 5"));
        Map<Long, String> categoryNameMap = buildCategoryNameMap(recentBills);
        vo.setRecentReviews(recentBills.stream().map(bill -> {
            BizBillVo billVo = new BizBillVo();
            BeanUtils.copyProperties(bill, billVo);
            if (bill.getCategoryId() != null) {
                billVo.setCategoryName(categoryNameMap.get(bill.getCategoryId()));
            }
            return billVo;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNonApprovedBillsByUser(String username) {
        // 查该用户所有非通过状态的票据
        List<BizBill> bills = this.lambdaQuery()
            .eq(BizBill::getCreateBy, username)
            .ne(BizBill::getStatus, "2")
            .list();
        if (bills.isEmpty()) return;

        List<Long> billIds = bills.stream().map(BizBill::getId).toList();

        // 删附件（含磁盘文件）
        deleteFilesAndRecords(
            new LambdaQueryWrapper<BizBillFile>().in(BizBillFile::getBillId, billIds));
        // 删审核记录
        auditLogMapper.delete(
            new LambdaQueryWrapper<BizAuditLog>().in(BizAuditLog::getBillId, billIds));
        // 删票据
        this.removeByIds(billIds);
    }

    @Override
    public List<ReviewTrendVo> getReviewTrend() {
        String username = SecurityUtils.getUsername();
        Calendar now = Calendar.getInstance();

        // 近12个月
        Calendar start = Calendar.getInstance();
        start.add(Calendar.MONTH, -11);
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        String startStr = new SimpleDateFormat("yyyy-MM-dd").format(start.getTime());
        String endStr = new SimpleDateFormat("yyyy-MM-dd").format(now.getTime());

        // 查该审核员近12个月审核过的票据（已通过+已退回）
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<BizBill>()
            .ge(BizBill::getAuditTime, startStr)
            .le(BizBill::getAuditTime, endStr)
            .eq(BizBill::getAuditBy, username)
            .in(BizBill::getStatus, "2", "3");
        List<BizBill> bills = this.list(wrapper);

        // 按月份分组统计
        Map<String, long[]> monthMap = new LinkedHashMap<>();
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM");
        for (int i = 11; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -i);
            String key = monthFmt.format(c.getTime());
            monthMap.put(key, new long[]{0, 0}); // [总量, 通过量]
        }

        for (BizBill bill : bills) {
            if (bill.getAuditTime() == null) continue;
            String key = monthFmt.format(bill.getAuditTime());
            long[] arr = monthMap.get(key);
            if (arr != null) {
                arr[0]++;
                if ("2".equals(bill.getStatus())) {
                    arr[1]++;
                }
            }
        }

        List<ReviewTrendVo> result = new ArrayList<>();
        monthMap.forEach((month, arr) -> {
            double rate = arr[0] > 0 ? Math.round(arr[1] * 1000.0 / arr[0]) / 10.0 : 0;
            result.add(ReviewTrendVo.builder()
                .month(month)
                .count(arr[0])
                .passRate(rate)
                .build());
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> getReviewComposition() {
        String username = SecurityUtils.getUsername();

        // 查该审核员审核通过的所有票据
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getStatus, "2")
            .eq(BizBill::getAuditBy, username);
        List<BizBill> bills = this.list(wrapper);

        // 按类别汇总金额
        Map<Long, Long> amountMap = new LinkedHashMap<>();
        Map<Long, String> nameMap = new HashMap<>();

        for (BizBill bill : bills) {
            Long cid = bill.getCategoryId();
            if (cid != null) {
                amountMap.merge(cid, bill.getAmount() != null ? bill.getAmount().longValue() : 0L, Long::sum);
                if (!nameMap.containsKey(cid)) {
                    BizCategory cat = categoryMapper.selectById(cid);
                    nameMap.put(cid, cat != null ? cat.getCategoryName() : "未知");
                }
            }
        }

        long total = amountMap.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> result = new ArrayList<>();
        amountMap.forEach((cid, amount) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryName", nameMap.getOrDefault(cid, "未知"));
            item.put("amount", amount);
            item.put("percent", total > 0 ? Math.round(amount * 1000.0 / total) / 10.0 : 0);
            result.add(item);
        });
        return result;
    }

}
