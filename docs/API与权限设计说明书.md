# 票据审核系统 — API与权限设计说明书

对 REST API 接口、请求/响应格式、权限矩阵、业务校验规则进行详细设计。

依据概要设计说明书，对 REST API 接口、权限矩阵、数据校验、业务规则进行详细设计。

---
## 4. DTO 设计

### 4.1 BizBillDto（新增/修改）

```java
@Data
public class BizBillDto {
    private Long id;                           // 修改时传
    @NotBlank(message = "票据标题不能为空")
    private String title;
    private Long categoryId;                   // 票据类别ID
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
    private String description;
    @NotBlank(message = "状态不能为空")
    private String status;                     // "0"=草稿 / "1"=直接提交
    private String attachment;                 // 逗号分隔的附件路径
}
```

### 4.2 BizBillReviewDto（审核）

```java
@Data
public class BizBillReviewDto {
    @NotNull(message = "票据ID不能为空")
    private Long billId;
    @NotBlank(message = "审核结果不能为空")
    private String action;                     // "1"=通过 / "2"=退回
    private String comment;                    // 审核意见
}
```

### 4.3 BizBillQueryDto（查询）

```java
@Data
public class BizBillQueryDto extends BasePageReq {
    private String keywords;                   // 搜索标题
    private Long categoryId;                   // 按类别过滤
    private String status;                     // 按状态过滤
    private String startTime;                  // 创建时间范围-开始
    private String endTime;                    // 创建时间范围-结束
}
```

---

## 5. VO 设计

| VO 类 | 用途 | 关键字段 |
|-------|------|----------|
| BizBillVo | 列表项 | id, billNo, title, categoryId, categoryName, amount, description, status, attachment, createBy, createTime, auditBy, auditTime, auditComment |
| BizBillDetailVo | 票据详情 | 全部字段 + categoryName + files(List\<BizBillFileVo\>) + auditLogs(List\<BizAuditLogVo\>) |
| BizBillFileVo | 附件信息 | id, fileName, filePath, fileSize, fileType, createTime |
| BizAuditLogVo | 审核记录 | id, action, comment, auditBy, auditTime |

---

## 6. API 详细规格

### 6.1 查询票据列表

```
POST /api/bill/query
```

请求体:

```json
{
  "pageNum": 1, "pageSize": 10,
  "keywords": "差旅", "categoryId": 1,
  "status": "1", "startTime": "2026-05-01", "endTime": "2026-05-21"
}
```

权限过滤:
- 有 `biz:bill:review` 权限 → 管理员: 查全部
- 无该权限 → 普通用户: `wrapper.eq(BizBill::getCreateBy, currentUsername)`

### 6.2 查看票据详情

```
GET /api/bill/{id}
```

响应含完整字段 + categoryName（JOIN biz_category）+ files（附件列表）+ auditLogs（审核历史列表）。非管理员只能查看自己的票据。

### 6.3 新增票据

```
POST /api/bill
```

处理逻辑:

```
1. DTO → Entity，createBy ← SecurityUtils.getUsername()
2. 草稿 (status="0"): billNo 设为 "DRAFT-" + 时间戳（满足 NOT NULL）
   直接提交 (status="1"): billNo ← generateBillNo()
3. save bill → save attachments
```

### 6.4 修改票据

```
PUT /api/bill
```

校验逻辑:

```
1. 查现有票据 → 不存在则抛异常
2. IF status NOT IN ("0", "3") → "只能编辑草稿或已退回状态的票据！"
3. IF createBy ≠ 当前用户 → "只能编辑自己的票据！"
4. 逐字段赋值更新，不覆盖 attachment（不传附件则保留原附件）
5. 仅当 dto.attachment 非空时: 删除旧附件 → 保存新附件
```

### 6.5 提交审核

```
POST /api/bill/submit/{id}
```

校验逻辑:

```
1. 查现有票据 → 不存在则抛异常
2. IF status NOT IN ("0", "3") → "只能提交草稿或已退回状态的票据！"
3. IF createBy ≠ 当前用户 → "只能提交自己的票据！"
4. 草稿的临时编号（DRAFT- 前缀）替换为正式 billNo，status ← "1"
```

### 6.6 删除票据

```
DELETE /api/bill/{id}
```

校验逻辑:

```
1. 查现有票据 → 不存在则抛异常
2. IF status ≠ "0" → "只能删除草稿状态的票据！"
3. IF createBy ≠ 当前用户 → "只能删除自己的票据！"
4. 删除所有附件记录 → 删除票据
```

### 6.7 审核票据

```
POST /api/bill/review
```

请求体:

```json
{ "billId": 1, "action": "1", "comment": "同意报销" }
```

校验逻辑:

```
1. 校验 action 值 → 非 "1" 或 "2" 则抛异常
2. 查现有票据 → 不存在则抛异常
3. IF status ≠ "1" → "只能审核已提交状态的票据！"
4. action="1": status ← "2"; action="2": status ← "3"
5. auditBy ← 当前用户, auditTime ← now
6. 更新票据 + 写入一条 BizAuditLog
```

---

### 6.8 操作日志自动导出配置

| 项 | 值 |
|------|------|
| 接口 | `GET /api/admin/operlog-export-interval` |
| 权限 | `biz:admin:list` |
| 返回 | `{ data: 10 }`（当前间隔分钟数，0=关闭） |

| 项 | 值 |
|------|------|
| 接口 | `PUT /api/admin/operlog-export-interval` |
| 权限 | `biz:admin:list` |
| 请求体 | `{ "interval": 10 }`（0-1440 分钟） |
| 说明 | 设 0 关闭，设 N>0 则每 N 分钟自动导出 Excel 并清空日志 |

定时任务 `OperLogAutoExportTask` 每 30 秒检查一次，距离上次导出时间超过配置间隔时触发导出。导出文件存放于项目根目录 `exports/` 文件夹。

---

## 7. 票据编号生成算法

```
generateBillNo():
    dateStr ← new SimpleDateFormat("yyyyMMdd").format(new Date())
    prefix ← "BILL-" + dateStr + "-"

    latest ← lambdaQuery()
        .likeRight(BizBill::getBillNo, prefix)
        .orderByDesc(BizBill::getBillNo)
        .last("LIMIT 1").one()

    IF latest == null:
        seq ← 1
    ELSE:
        seq ← Integer.parseInt(latest.billNo.split("-")[2]) + 1

    RETURN String.format("BILL-%s-%04d", dateStr, seq)
```

并发安全: bill_no 有 UNIQUE 约束，冲突时 MySQL 抛 DuplicateKeyException。

---

## 8. 前端组件树

```
index.vue (我的票据)
├─ SearchForm.vue   Props: queryParams, showSearch   Events: @query, @reset
├─ BillTable.vue    Props: billList, loading, total   Events: @add, @edit, @delete, @detail, @submit
├─ BillForm.vue     Props: modelValue, title, formData  Events: @update:modelValue, @success
└─ BillDetail.vue   Props: modelValue, billData       Events: @update:modelValue
```

### 8.1 表格列设计

| 列 | prop | 宽度 | 渲染 |
|----|------|------|------|
| 票据编号 | billNo | 180 | show-overflow-tooltip |
| 标题 | title | 200 | show-overflow-tooltip |
| 类别 | categoryName | 100 | text |
| 金额 | amount | 120 | 右对齐 ¥0.00 |
| 状态 | status | 100 | dict-tag（字典 `biz_bill_status`） |
| 创建时间 | createTime | 160 | parseTime |
| 操作 | — | 240 | 动态显示 |

### 8.2 操作列按钮显隐

| 按钮 | 显示条件 | 权限 |
|------|----------|------|
| 详情 | 始终 | v-hasPermi="['biz:bill:query']" |
| 编辑 | status IN ('0','3') | v-hasPermi="['biz:bill:add']" |
| 提交 | status IN ('0','3') | v-hasPermi="['biz:bill:add']" |
| 删除 | status === '0' | v-hasPermi="['biz:bill:remove']" |

### 8.3 表单字段

| 字段 | 组件 | 规则 |
|------|------|------|
| 票据标题 | el-input | required, maxlength=200 |
| 票据类别 | el-select | 选项来自 biz_category 表 |
| 金额 | el-input-number | required, min=0, precision=2 |
| 描述 | el-input textarea | rows=4 |
| 附件 | FileUpload | 多文件上传 |

### 8.4 双按钮逻辑

```javascript
function handleSaveDraft() { form.status = "0"; submitForm(); }
function handleSubmit()   { form.status = "1"; submitForm(); }
```

---
