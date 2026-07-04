package com.zsc.module.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zsc.module.common.pagination.PageResult;
import com.zsc.module.domain.dto.BizBillDto;
import com.zsc.module.domain.dto.BizBillReviewDto;
import com.zsc.module.domain.dto.query.BizBillQueryDto;
import com.zsc.module.domain.entity.BizBill;
import com.zsc.module.domain.vo.BizBillDetailVo;
import com.zsc.module.domain.vo.BizBillVo;
import com.zsc.module.domain.vo.TrendItemVo;

import com.zsc.module.domain.vo.ReviewerStatsVo;
import com.zsc.module.domain.vo.ReviewTrendVo;

import java.util.List;
import java.util.Map;

/**
 * 票据表 服务类
 */
public interface BizBillService extends IService<BizBill> {

    /**
     * 新增票据
     */
    void addBill(BizBillDto dto);

    /**
     * 分页条件查询票据列表
     */
    PageResult<BizBillVo> queryBills(BizBillQueryDto dto);

    /**
     * 查看票据详情（含附件列表和审核历史）
     */
    BizBillDetailVo getBillDetail(Long id);

    /**
     * 编辑票据（仅限草稿或已退回状态）
     */
    void updateBill(BizBillDto dto);

    /**
     * 提交草稿进入审核流程
     */
    void submitBill(Long id);

    /**
     * 删除草稿票据及其附件
     */
    void deleteBill(Long id);

    /**
     * 审核票据（通过或退回）
     */
    void reviewBill(BizBillReviewDto dto);

    List<TrendItemVo> getMonthlyTrend();

    List<TrendItemVo> getCategoryAmountSummary();

    /**
     * 审核员仪表盘统计数据
     */
    ReviewerStatsVo getReviewerStats();

    /**
     * 删除用户时清理其非通过状态的票据（草稿/待审/退回 + 附件 + 审核记录）
     */
    void deleteNonApprovedBillsByUser(String username);

    /**
     * 近12个月审核趋势（审核单量 + 通过率）
     */
    List<ReviewTrendVo> getReviewTrend();

    /**
     * 审核构成分析：按费用类型统计通过金额占比
     */
    List<Map<String, Object>> getReviewComposition();

}
