package com.zsc.module.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核趋势数据项（近12个月）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTrendVo {
    /** 月份标签（如 "2025-07"） */
    private String month;
    /** 审核单量 */
    private long count;
    /** 通过率（百分比 0-100） */
    private double passRate;
}
