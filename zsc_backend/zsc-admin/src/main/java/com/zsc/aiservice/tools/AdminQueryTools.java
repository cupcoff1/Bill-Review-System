package com.zsc.aiservice.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zsc.common.core.domain.entity.SysRole;
import com.zsc.common.core.domain.entity.SysUser;
import com.zsc.module.domain.entity.BizAuditLog;
import com.zsc.module.domain.entity.BizBill;
import com.zsc.module.domain.entity.BizCategory;
import com.zsc.module.domain.vo.ReviewerWorkloadVo;
import com.zsc.module.mapper.BizAuditLogMapper;
import com.zsc.module.service.BizBillService;
import com.zsc.module.service.BizCategoryService;
import com.zsc.system.mapper.SysRoleMapper;
import com.zsc.system.mapper.SysUserMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员查询工具集
 * 暴露给 AI 的 10 个 @Tool 方法，全部只读，复用已有 Service/Mapper
 */
@Component
public class AdminQueryTools {

    @Autowired
    private BizBillService billService;

    @Autowired
    private BizCategoryService categoryService;

    @Autowired
    private BizAuditLogMapper auditLogMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== Tool 1: 系统全景统计 ====================

    @Tool("获取系统全景统计：用户总数、各角色人数、票据总量及各状态数量。用于管理员了解系统整体运行状况")
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("总用户数", userMapper.countUser());
        stats.put("超级管理员数", userMapper.countUserByRoleKey("admin"));
        stats.put("系统管理员数", userMapper.countUserByRoleKey("admin_user"));
        stats.put("审核员数", userMapper.countUserByRoleKey("reviewer"));
        stats.put("普通用户数", userMapper.countUserByRoleKey("user"));
        stats.put("票据总量", billService.count());
        stats.put("待审核数", billService.count(
                new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "1")));
        stats.put("已通过数", billService.count(
                new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "2")));
        stats.put("已退回数", billService.count(
                new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "3")));
        stats.put("草稿数", billService.count(
                new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "0")));
        return stats;
    }

    // ==================== Tool 2: 审核员工作量排行 ====================

    @Tool("获取审核员工作量排行。返回已格式化的 Markdown 表格文本。")
    public String getReviewerWorkload() {
        List<BizBill> bills = billService.list(
                new LambdaQueryWrapper<BizBill>()
                        .isNotNull(BizBill::getAuditBy)
                        .in(BizBill::getStatus, "2", "3"));

        Map<String, ReviewerWorkloadVo> map = new LinkedHashMap<>();
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

        List<ReviewerWorkloadVo> sorted = map.values().stream()
                .sorted(Comparator.comparingLong(ReviewerWorkloadVo::getTotalCount).reversed())
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return "暂无审核记录。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 **").append(sorted.size()).append("** 位审核员参与审核。\n\n");
        sb.append("| 审核员 | 审核总量 | 通过数 | 退回数 | 通过率 |\n");
        sb.append("|--------|----------|--------|--------|--------|\n");
        for (ReviewerWorkloadVo w : sorted) {
            long total = w.getApprovedCount() + w.getRejectedCount();
            String rate = total > 0
                    ? String.format("%.1f%%", 100.0 * w.getApprovedCount() / total) : "0%";
            sb.append("| ").append(w.getReviewerName())
                    .append(" | ").append(w.getTotalCount())
                    .append(" | ").append(w.getApprovedCount())
                    .append(" | ").append(w.getRejectedCount())
                    .append(" | ").append(rate)
                    .append(" |\n");
        }

        return sb.toString();
    }

    // ==================== Tool 3: 多条件搜索票据 ====================

    @Tool("按条件搜索票据：支持按状态、类别ID、提交人、审核人、关键词(标题/编号)、日期范围组合查询。" +
            "所有参数均可选，AI根据用户意图传入相关参数")
    public List<Map<String, Object>> searchBills(
            @P("状态：0草稿 1待审核 2已通过 3已退回") String status,
            @P("类别ID") Long categoryId,
            @P("提交人用户名") String createBy,
            @P("审核人用户名") String auditBy,
            @P("搜索关键词，匹配标题和票据编号") String keyword,
            @P("起始日期 yyyy-MM-dd") String startDate,
            @P("截止日期 yyyy-MM-dd") String endDate) {

        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(BizBill::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(BizBill::getCategoryId, categoryId);
        }
        if (createBy != null && !createBy.isEmpty()) {
            wrapper.eq(BizBill::getCreateBy, createBy);
        }
        if (auditBy != null && !auditBy.isEmpty()) {
            wrapper.eq(BizBill::getAuditBy, auditBy);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(BizBill::getTitle, keyword)
                    .or()
                    .like(BizBill::getBillNo, keyword));
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(BizBill::getCreateTime, parseDateStart(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(BizBill::getCreateTime, parseDateEnd(endDate));
        }

        wrapper.orderByDesc(BizBill::getCreateTime);
        // 限制最多返回30条，避免单次返回过多
        wrapper.last("LIMIT 30");

        List<BizBill> bills = billService.list(wrapper);

        if (bills.isEmpty()) {
            return List.of(Map.of("info", "未找到符合条件的票据"));
        }

        // 批量加载类别名称
        Set<Long> catIds = bills.stream()
                .map(BizBill::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> catNameMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            categoryService.listByIds(catIds)
                    .forEach(c -> catNameMap.put(c.getCategoryId(), c.getCategoryName()));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizBill bill : bills) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", bill.getId());
            m.put("billNo", bill.getBillNo());
            m.put("title", bill.getTitle());
            m.put("amount", bill.getAmount() != null ? bill.getAmount().toString() : "0.00");
            m.put("status", statusLabel(bill.getStatus()));
            m.put("categoryName", catNameMap.getOrDefault(bill.getCategoryId(), "未知"));
            m.put("createBy", bill.getCreateBy());
            m.put("createTime", fmt(bill.getCreateTime()));
            m.put("auditBy", bill.getAuditBy());
            m.put("auditComment", bill.getAuditComment());
            result.add(m);
        }
        return result;
    }

    // ==================== Tool 4: 各类别金额汇总 ====================

    @Tool("获取各类别已通过票据的金额汇总，含占比。用于了解支出结构分布")
    public List<Map<String, Object>> getCategorySummary() {
        List<BizBill> approvedBills = billService.list(
                new LambdaQueryWrapper<BizBill>().eq(BizBill::getStatus, "2"));

        if (approvedBills.isEmpty()) {
            return List.of(Map.of("info", "暂无已通过票据"));
        }

        // 按类别ID汇总
        Map<Long, Long> amountMap = new LinkedHashMap<>();
        for (BizBill b : approvedBills) {
            Long cid = b.getCategoryId();
            long amt = b.getAmount() != null ? b.getAmount().longValue() : 0L;
            amountMap.merge(cid != null ? cid : 0L, amt, Long::sum);
        }

        // 加载类别名称
        Map<Long, String> catNames = new HashMap<>();
        if (!amountMap.isEmpty()) {
            categoryService.listByIds(amountMap.keySet())
                    .forEach(c -> catNames.put(c.getCategoryId(), c.getCategoryName()));
        }

        long total = amountMap.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Long> e : amountMap.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoryName", catNames.getOrDefault(e.getKey(), "未分类"));
            m.put("amount", e.getValue().toString());
            m.put("percent", total > 0 ?
                    String.format("%.1f%%", 100.0 * e.getValue() / total) : "0%");
            result.add(m);
        }
        result.sort((a, b) -> {
            long va = Long.parseLong((String) b.get("amount"));
            long vb = Long.parseLong((String) a.get("amount"));
            return Long.compare(va, vb);
        });
        return result;
    }

    // ==================== Tool 5: 月度提交趋势 ====================

    @Tool("获取本月票据提交的周度趋势，按自然周统计提交量")
    public List<Map<String, Object>> getMonthlyTrend() {
        List<com.zsc.module.domain.vo.TrendItemVo> trends = billService.getMonthlyTrend();
        return trends.stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", t.getLabel());
                    m.put("count", t.getCount());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ==================== Tool 6: 日报摘要 ====================

    @Tool("获取指定日期的运行日报：新提交数、审核通过数、审核退回数、退回率、" +
            "积压数量(超3天未审)和详情、当日审核员审核排行。" +
            "管理员问'日报'或'今天怎么样'时调用此工具")
    public Map<String, Object> getDailyDigest(
            @P("日期 yyyy-MM-dd，不传默认今天") String date) {

        LocalDate targetDate = (date != null && !date.isEmpty())
                ? LocalDate.parse(date, DATE_FMT) : LocalDate.now();

        Date dayStart = toDate(targetDate);
        Date dayEnd = toDate(targetDate.plusDays(1));

        // 当日提交
        long submitted = billService.count(new LambdaQueryWrapper<BizBill>()
                .between(BizBill::getCreateTime, dayStart, dayEnd));

        // 当日审核通过
        long approved = billService.count(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, "2")
                .between(BizBill::getAuditTime, dayStart, dayEnd));

        // 当日退回
        long rejected = billService.count(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, "3")
                .between(BizBill::getAuditTime, dayStart, dayEnd));

        long audited = approved + rejected;
        String rejectionRate = audited > 0 ?
                String.format("%.1f%%", 100.0 * rejected / audited) : "N/A";

        // 积压（待审超3天）
        Date threeDaysAgo = toDate(targetDate.minusDays(3));
        List<BizBill> staleBills = billService.list(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, "1")
                .lt(BizBill::getCreateTime, threeDaysAgo)
                .orderByAsc(BizBill::getCreateTime));

        List<Map<String, String>> staleInfo = staleBills.stream()
                .map(b -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("billNo", b.getBillNo());
                    m.put("title", b.getTitle());
                    m.put("createBy", b.getCreateBy());
                    m.put("createTime", fmt(b.getCreateTime()));
                    return m;
                })
                .collect(Collectors.toList());

        // 当日审核员排行
        List<BizBill> todayAudited = billService.list(new LambdaQueryWrapper<BizBill>()
                .in(BizBill::getStatus, "2", "3")
                .between(BizBill::getAuditTime, dayStart, dayEnd));

        Map<String, Long> reviewerCount = new LinkedHashMap<>();
        for (BizBill b : todayAudited) {
            String name = b.getAuditBy();
            if (name != null) reviewerCount.merge(name, 1L, Long::sum);
        }
        List<Map<String, Object>> ranking = reviewerCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> Map.<String, Object>of("审核员", e.getKey(), "审核数", e.getValue()))
                .collect(Collectors.toList());

        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("日期", targetDate.format(DATE_FMT));
        digest.put("新提交数", submitted);
        digest.put("审核通过数", approved);
        digest.put("审核退回数", rejected);
        digest.put("退回率", rejectionRate);
        digest.put("积压数(超3天)", staleBills.size());
        digest.put("积压详情", staleInfo);
        digest.put("当日审核排行", ranking);
        return digest;
    }

    // ==================== Tool 7: 周报 ====================

    @Tool("获取本周运行周报：本周 vs 上周提交量环比变化、通过率对比、" +
            "各类别金额分布、审核员工作量排行（含上周对比）")
    public Map<String, Object> getWeeklyReport() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = thisMonday.minusDays(1);

        Date thisWeekStart = toDate(thisMonday);

        // 本周数据
        long thisWeekSubmitted = countSubmittedBetween(thisWeekStart, toDate(today.plusDays(1)));
        long thisWeekApproved = countAuditedBetween("2", thisWeekStart, toDate(today.plusDays(1)));
        long thisWeekRejected = countAuditedBetween("3", thisWeekStart, toDate(today.plusDays(1)));
        long thisWeekAudited = thisWeekApproved + thisWeekRejected;

        // 上周数据
        Date lastWeekStart = toDate(lastMonday);
        Date lastWeekEnd = toDate(lastSunday.plusDays(1));
        long lastWeekSubmitted = countSubmittedBetween(lastWeekStart, lastWeekEnd);
        long lastWeekApproved = countAuditedBetween("2", lastWeekStart, lastWeekEnd);
        long lastWeekRejected = countAuditedBetween("3", lastWeekStart, lastWeekEnd);
        long lastWeekAudited = lastWeekApproved + lastWeekRejected;

        String submittedChange = calcChange(thisWeekSubmitted, lastWeekSubmitted);
        String approvedChange = calcChange(thisWeekApproved, lastWeekApproved);
        String rejectedChange = calcChange(thisWeekRejected, lastWeekRejected);

        // 本周类别金额分布
        List<Map<String, Object>> categoryDist = getCategorySummary();

        // 审核员工作量
        String workload = getReviewerWorkload();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("报告期间", thisMonday.format(DATE_FMT) + " ~ " + today.format(DATE_FMT));
        report.put("本周提交数", thisWeekSubmitted);
        report.put("上周提交数", lastWeekSubmitted);
        report.put("提交数环比变化", submittedChange);
        report.put("本周通过数", thisWeekApproved);
        report.put("上周通过数", lastWeekApproved);
        report.put("通过数环比变化", approvedChange);
        report.put("本周退回数", thisWeekRejected);
        report.put("上周退回数", lastWeekRejected);
        report.put("退回数环比变化", rejectedChange);
        report.put("本周通过率", thisWeekAudited > 0 ?
                String.format("%.1f%%", 100.0 * thisWeekApproved / thisWeekAudited) : "N/A");
        report.put("上周通过率", lastWeekAudited > 0 ?
                String.format("%.1f%%", 100.0 * lastWeekApproved / lastWeekAudited) : "N/A");
        report.put("各类别金额分布", categoryDist);
        report.put("审核员工作量排行", workload);
        return report;
    }

    // ==================== Tool 8: 用户画像 ====================

    @Tool("获取指定用户的行为画像：基本信息、提交统计(总数/通过率)、金额特征(均值/最高/累计)、" +
            "偏好类别分布、退回原因分析、提交习惯(本月/上月/月均/趋势方向)、风险标记。" +
            "管理员问'分析XXX'或'XXX这个人怎么样'时调用此工具")
    public Map<String, Object> getUserProfile(@P("用户名") String userName) {
        // 1. 用户基本信息
        SysUser sysUser = userMapper.selectUserByUserName(userName);
        if (sysUser == null) {
            return Map.of("found", false, "message", "未找到用户：" + userName);
        }

        Map<String, Object> profile = new LinkedHashMap<>();

        // 基本信息
        Map<String, Object> basicInfo = new LinkedHashMap<>();
        basicInfo.put("用户名", sysUser.getUserName());
        basicInfo.put("昵称", sysUser.getNickName());
        basicInfo.put("邮箱", sysUser.getEmail());
        basicInfo.put("手机号", sysUser.getPhonenumber());
        basicInfo.put("状态", "0".equals(sysUser.getStatus()) ? "正常" : "停用");
        basicInfo.put("注册时间", fmt(sysUser.getCreateTime()));

        // 角色
        List<SysRole> roles = roleMapper.selectRolesByUserName(userName);
        if (roles != null && !roles.isEmpty()) {
            basicInfo.put("角色", roles.stream()
                    .map(SysRole::getRoleName).collect(Collectors.joining(", ")));
        }
        profile.put("基本信息", basicInfo);

        // 2. 票据统计
        List<BizBill> allBills = billService.list(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getCreateBy, userName));

        long total = allBills.size();
        long draft = allBills.stream().filter(b -> "0".equals(b.getStatus())).count();
        long pending = allBills.stream().filter(b -> "1".equals(b.getStatus())).count();
        long approved = allBills.stream().filter(b -> "2".equals(b.getStatus())).count();
        long rejected = allBills.stream().filter(b -> "3".equals(b.getStatus())).count();
        long finished = approved + rejected;

        Map<String, Object> submitStats = new LinkedHashMap<>();
        submitStats.put("总提交", total);
        submitStats.put("草稿", draft);
        submitStats.put("待审核", pending);
        submitStats.put("已通过", approved);
        submitStats.put("已退回", rejected);
        submitStats.put("通过率", finished > 0 ?
                String.format("%.1f%%", 100.0 * approved / finished) : "N/A");
        profile.put("提交统计", submitStats);

        // 3. 金额特征
        if (!allBills.isEmpty()) {
            List<Long> amounts = allBills.stream()
                    .map(b -> b.getAmount() != null ? b.getAmount().longValue() : 0L)
                    .sorted().collect(Collectors.toList());
            long sum = amounts.stream().mapToLong(Long::longValue).sum();

            Map<String, Object> amountStats = new LinkedHashMap<>();
            amountStats.put("均值", String.format("%.2f", 1.0 * sum / amounts.size()));
            amountStats.put("最高", amounts.get(amounts.size() - 1).toString());
            amountStats.put("最低", amounts.get(0).toString());
            amountStats.put("累计总额", String.valueOf(sum));
            profile.put("金额特征", amountStats);
        }

        // 4. 偏好类别
        Map<Long, Long> catCounts = new LinkedHashMap<>();
        for (BizBill b : allBills) {
            Long cid = b.getCategoryId();
            if (cid != null) catCounts.merge(cid, 1L, Long::sum);
        }
        if (!catCounts.isEmpty()) {
            Map<Long, String> catNames = new HashMap<>();
            categoryService.listByIds(catCounts.keySet())
                    .forEach(c -> catNames.put(c.getCategoryId(), c.getCategoryName()));
            List<Map<String, String>> catList = catCounts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .map(e -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("类别", catNames.getOrDefault(e.getKey(), "未知"));
                        m.put("数量", e.getValue().toString());
                        m.put("占比", String.format("%.0f%%", 100.0 * e.getValue() / total));
                        return m;
                    }).collect(Collectors.toList());
            profile.put("偏好类别", catList);
        }

        // 5. 退回原因分析
        List<Long> rejectedBillIds = allBills.stream()
                .filter(b -> "3".equals(b.getStatus()))
                .map(BizBill::getId).collect(Collectors.toList());
        if (!rejectedBillIds.isEmpty()) {
            List<BizAuditLog> rejectLogs = auditLogMapper.selectList(
                    new LambdaQueryWrapper<BizAuditLog>()
                            .in(BizAuditLog::getBillId, rejectedBillIds)
                            .eq(BizAuditLog::getAction, "2"));
            Map<String, Long> reasonCount = new LinkedHashMap<>();
            for (BizAuditLog log : rejectLogs) {
                String comment = log.getComment();
                if (comment != null && !comment.isEmpty()) {
                    reasonCount.merge(comment, 1L, Long::sum);
                }
            }
            if (!reasonCount.isEmpty()) {
                List<Map<String, String>> reasonList = reasonCount.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> {
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("原因", e.getKey());
                            m.put("次数", e.getValue().toString());
                            return m;
                        }).collect(Collectors.toList());
                profile.put("退回原因分析", reasonList);
            }
        }

        // 6. 提交习惯（本月/上月/月均/趋势）
        LocalDate now = LocalDate.now();
        LocalDate thisMonthStart = now.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        LocalDate lastMonthEnd = thisMonthStart.minusDays(1);

        long thisMonthCount = allBills.stream()
                .filter(b -> {
                    LocalDate d = toLocalDate(b.getCreateTime());
                    return d != null && !d.isBefore(thisMonthStart);
                }).count();
        long lastMonthCount = allBills.stream()
                .filter(b -> {
                    LocalDate d = toLocalDate(b.getCreateTime());
                    return d != null && !d.isBefore(lastMonthStart) && d.isBefore(lastMonthEnd.plusDays(1));
                }).count();

        // 计算月均（从第一张票据到现在的月份数）
        double monthlyAvg = total;
        if (!allBills.isEmpty()) {
            BizBill first = allBills.stream()
                    .filter(b -> b.getCreateTime() != null)
                    .min(Comparator.comparing(BizBill::getCreateTime)).orElse(null);
            if (first != null) {
                LocalDate firstDate = toLocalDate(first.getCreateTime());
                if (firstDate != null) {
                    long months = java.time.temporal.ChronoUnit.MONTHS.between(firstDate, now) + 1;
                    if (months > 0) monthlyAvg = 1.0 * total / months;
                }
            }
        }

        Map<String, Object> habits = new LinkedHashMap<>();
        habits.put("本月提交", thisMonthCount);
        habits.put("上月提交", lastMonthCount);
        habits.put("月均提交", String.format("%.1f", monthlyAvg));
        if (lastMonthCount > 0) {
            habits.put("环比趋势", thisMonthCount > lastMonthCount ? "上升" :
                    thisMonthCount < lastMonthCount ? "下降" : "持平");
        }
        profile.put("提交习惯", habits);

        // 7. 风险标记
        List<String> risks = new ArrayList<>();
        if (thisMonthCount > monthlyAvg * 1.5) {
            risks.add("本月提交量突增（月均 " + String.format("%.0f", monthlyAvg) +
                    " 单，本月已 " + thisMonthCount + " 单）");
        }
        if (finished > 0 && rejected > 0) {
            double rejectRate = 1.0 * rejected / finished;
            if (rejectRate > 0.5) {
                risks.add("退回率偏高（" + String.format("%.0f%%", 100 * rejectRate) + "），建议关注提交质量");
            }
        }
        profile.put("风险标记", risks.isEmpty() ? "无" : risks);

        return profile;
    }

    // ==================== Tool 9: 提交量排名 ====================

    @Tool("获取提交量排名（默认不限时间，全部历史数据），管理员问'谁提交最多'或'提交排名'时调用此工具。" +
            "返回已格式化的 Markdown 表格文本。")
    public String getTopSubmitters(
            @P("起始日期 yyyy-MM-dd，不传则从最早开始") String startDate,
            @P("截止日期 yyyy-MM-dd，不传则到今天") String endDate,
            @P("返回前N名，默认10") int topN) {

        if (topN <= 0) topN = 10;

        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(BizBill::getCreateTime, parseDateStart(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(BizBill::getCreateTime, parseDateEnd(endDate));
        }
        wrapper.orderByDesc(BizBill::getCreateTime);

        List<BizBill> bills = billService.list(wrapper);

        // 按创建者分组统计 [total, approved]
        Map<String, long[]> creatorStats = new LinkedHashMap<>();
        for (BizBill b : bills) {
            String name = b.getCreateBy();
            if (name == null) continue;
            long[] stats = creatorStats.computeIfAbsent(name, k -> new long[2]);
            stats[0]++;
            if ("2".equals(b.getStatus())) stats[1]++;
        }

        if (creatorStats.isEmpty()) {
            return "暂无提交记录。";
        }

        List<Map.Entry<String, long[]>> sorted = creatorStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(topN)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("共 **").append(creatorStats.size()).append("** 位用户提交过票据，前 **").append(sorted.size()).append("** 名：\n\n");
        sb.append("| 排名 | 用户名 | 提交数 | 通过数 | 通过率 |\n");
        sb.append("|------|--------|--------|--------|--------|\n");
        int rank = 0;
        for (Map.Entry<String, long[]> e : sorted) {
            rank++;
            long[] v = e.getValue();
            String rate = v[0] > 0 ? String.format("%.1f%%", 100.0 * v[1] / v[0]) : "N/A";
            sb.append("| ").append(rank)
                    .append(" | ").append(e.getKey())
                    .append(" | ").append(v[0])
                    .append(" | ").append(v[1])
                    .append(" | ").append(rate)
                    .append(" |\n");
        }

        return sb.toString();
    }

    // ==================== Tool 10: 用户列表 ====================

    @Tool("获取系统中普通用户和审核员列表（不含超管和系统管理员，出于权限安全考虑）。" +
            "管理员问'用户管理'、'列出所有用户'、'有哪些用户'时调用此工具。" +
            "返回已格式化的 Markdown 表格文本。")
    public String listUsers() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysUser> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 9999);
        List<SysUser> users = userMapper.selectUserWithRoles(page, null, null, null, "admin_user");

        // 额外过滤超管
        List<SysUser> filtered = new ArrayList<>();
        int adminCount = 0, superAdminCount = 0;
        for (SysUser u : users) {
            boolean isSuperAdmin = false, isAdminUser = false;
            if (u.getRoles() != null) {
                for (SysRole r : u.getRoles()) {
                    if ("admin".equals(r.getRoleKey())) isSuperAdmin = true;
                    if ("admin_user".equals(r.getRoleKey())) isAdminUser = true;
                }
            }
            if (isSuperAdmin) { superAdminCount++; continue; }
            if (isAdminUser) { adminCount++; continue; }
            filtered.add(u);
        }

        if (filtered.isEmpty()) {
            return "系统中暂无普通用户或审核员。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 **").append(filtered.size()).append("** 位用户。\n");
        if (superAdminCount > 0 || adminCount > 0) {
            sb.append("（*因权限限制，");
            if (superAdminCount > 0) sb.append(superAdminCount).append(" 位超级管理员");
            if (superAdminCount > 0 && adminCount > 0) sb.append("、");
            if (adminCount > 0) sb.append(adminCount).append(" 位系统管理员");
            sb.append("不在列表中。*）\n");
        }
        sb.append("\n");
        sb.append("| 用户名 | 邮箱 | 角色 | 状态 | 注册时间 |\n");
        sb.append("|--------|------|------|------|----------|\n");
        for (SysUser u : filtered) {
            String roleName = (u.getRoles() != null && !u.getRoles().isEmpty())
                    ? u.getRoles().stream().map(r -> r.getRoleName()).collect(Collectors.joining("、"))
                    : "无角色";
            String status = "0".equals(u.getStatus()) ? "正常" : "停用";
            sb.append("| ").append(u.getUserName())
                    .append(" | ").append(u.getEmail() != null ? u.getEmail() : "-")
                    .append(" | ").append(roleName)
                    .append(" | ").append(status)
                    .append(" | ").append(fmt(u.getCreateTime()))
                    .append(" |\n");
        }

        return sb.toString();
    }

    // ==================== Tool 11: 类别列表 ====================

    @Tool("获取系统中所有票据类别列表。管理员问'类别管理'、'列出所有类别'、'有哪些类别'时调用此工具。" +
            "返回已格式化的 Markdown 表格文本。")
    public String listCategories() {
        List<BizCategory> categories = categoryService.list(
                new LambdaQueryWrapper<BizCategory>().orderByAsc(BizCategory::getSortOrder));

        if (categories == null || categories.isEmpty()) {
            return "系统中暂无类别。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 **").append(categories.size()).append("** 个类别：\n\n");
        sb.append("| 类别名称 | 排序 | 状态 | 创建时间 |\n");
        sb.append("|----------|------|------|----------|\n");
        for (BizCategory c : categories) {
            String status = "0".equals(c.getStatus()) ? "启用" : "停用";
            sb.append("| ").append(c.getCategoryName())
                    .append(" | ").append(c.getSortOrder() != null ? c.getSortOrder() : 0)
                    .append(" | ").append(status)
                    .append(" | ").append(fmt(c.getCreateTime()))
                    .append(" |\n");
        }

        return sb.toString();
    }

    // ==================== Tool 12: 待审核概览 ====================

    @Tool("获取当前待审核票据概览。管理员问'待审核情况'或'还有多少单没审'时调用此工具。" +
            "返回已格式化的 Markdown 文本，直接展示给用户。")
    public String getPendingBills() {
        List<BizBill> pendingBills = billService.list(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, "1")
                .orderByAsc(BizBill::getCreateTime));

        if (pendingBills.isEmpty()) {
            return "当前没有待审核票据。";
        }

        // 积压（超3天）
        Date threeDaysAgo = toDate(LocalDate.now().minusDays(3));
        long staleCount = pendingBills.stream()
                .filter(b -> b.getCreateTime() != null && b.getCreateTime().before(threeDaysAgo))
                .count();

        // 批量加载类别名称
        Set<Long> catIds = pendingBills.stream()
                .map(BizBill::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> catNameMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            categoryService.listByIds(catIds)
                    .forEach(c -> catNameMap.put(c.getCategoryId(), c.getCategoryName()));
        }

        StringBuilder sb = new StringBuilder();

        // 摘要
        sb.append("**待审核总数**：").append(pendingBills.size()).append(" 单");
        if (staleCount > 0) {
            sb.append("，其中 ⚠️ **积压（超3天）**：").append(staleCount).append(" 单");
        }
        sb.append("\n\n");

        // 类别分布
        Map<Long, Long> catCounts = new LinkedHashMap<>();
        for (BizBill b : pendingBills) {
            catCounts.merge(b.getCategoryId() != null ? b.getCategoryId() : 0L, 1L, Long::sum);
        }
        if (!catCounts.isEmpty()) {
            sb.append("### 各类别待审分布\n\n");
            sb.append("| 类别 | 待审数 |\n");
            sb.append("|------|--------|\n");
            catCounts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .forEach(e -> {
                        String name = catNameMap.getOrDefault(e.getKey(), "未分类");
                        sb.append("| ").append(name).append(" | ").append(e.getValue()).append(" |\n");
                    });
            sb.append("\n");
        }

        // 完整表格
        int limit = Math.min(pendingBills.size(), 50);
        sb.append("### 待审核票据完整列表（").append(limit).append(" 条）\n\n");
        sb.append("| 票据编号 | 标题 | 金额(元) | 类别 | 提交人 | 提交时间 | 状态 |\n");
        sb.append("|----------|------|----------|------|--------|----------|------|\n");
        for (int i = 0; i < limit; i++) {
            BizBill b = pendingBills.get(i);
            String stale = (b.getCreateTime() != null && b.getCreateTime().before(threeDaysAgo)) ? "⚠️积压" : "正常";
            String title = b.getTitle() != null && b.getTitle().length() > 14
                    ? b.getTitle().substring(0, 14) + "…" : b.getTitle();
            sb.append("| ").append(b.getBillNo())
                    .append(" | ").append(title)
                    .append(" | ").append(b.getAmount() != null ? b.getAmount().toString() : "0.00")
                    .append(" | ").append(catNameMap.getOrDefault(b.getCategoryId(), "未分类"))
                    .append(" | ").append(b.getCreateBy())
                    .append(" | ").append(fmt(b.getCreateTime()))
                    .append(" | ").append(stale)
                    .append(" |\n");
        }

        return sb.toString();
    }

    // ==================== Markdown 表格格式化 ====================

    /**
     * 将结构化数据转为 Markdown 管道表格字符串。
     * LLM 拿到后直接用，无需自己排版。
     */
    private String toMarkdownTable(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        // 分隔行
        sb.append("| ").append(headers.stream().map(h -> "---").collect(Collectors.joining(" | "))).append(" |\n");
        // 数据行
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private String statusLabel(String s) {
        if (s == null) return "未知";
        return switch (s) {
            case "0" -> "草稿";
            case "1" -> "待审核";
            case "2" -> "已通过";
            case "3" -> "已退回";
            default -> "未知(" + s + ")";
        };
    }

    private String fmt(Date date) {
        if (date == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date parseDateStart(String dateStr) {
        return toDate(LocalDate.parse(dateStr, DATE_FMT));
    }

    private Date parseDateEnd(String dateStr) {
        return toDate(LocalDate.parse(dateStr, DATE_FMT).plusDays(1));
    }

    private long countSubmittedBetween(Date start, Date end) {
        return billService.count(new LambdaQueryWrapper<BizBill>()
                .between(BizBill::getCreateTime, start, end));
    }

    private long countAuditedBetween(String status, Date start, Date end) {
        return billService.count(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getStatus, status)
                .between(BizBill::getAuditTime, start, end));
    }

    private String calcChange(long current, long previous) {
        if (previous == 0) return current > 0 ? "新增（上周为0）" : "持平（均为0）";
        double change = 100.0 * (current - previous) / previous;
        String sign = change >= 0 ? "+" : "";
        return sign + String.format("%.1f%%", change);
    }
}
