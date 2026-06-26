# 票据审核系统运维知识库

## 角色体系
- 超级管理员(admin)：系统初始化时创建，拥有全部权限
- 系统管理员(admin_user)：由超管创建，管理用户、类别、审核注册申请
- 审核员(reviewer)：审核票据，通过或退回
- 普通用户(user)：提交票据

## 票据状态流转
草稿(0) → 已提交/待审核(1) → 审核通过(2) / 审核退回(3)

## 审核操作
审核通过(action=1) → 票据归档
审核退回(action=2) → 提交人可修改后重新提交

## 票据编号规则
格式 BILL-yyyyMMdd-{主键ID}，如 BILL-20260625-1001
提交时自动生成，草稿使用临时编号

## 类别说明
办公用品、差旅交通、招待餐饮、其他（默认兜底类别）
删除类别时关联票据自动归入"其他"

## 统计口径
- 退回率 = 退回数 / (通过数 + 退回数)
- 通过率 = 通过数 / (通过数 + 退回数)
- 积压定义：status=1 且 createTime 超过3天的票据
- 审核效率 = 审核数 / 工作小时数

## 表格字段
biz_bill：id, bill_no, title, category_id, amount(decimal), description, status, create_by, create_time, audit_by, audit_time, audit_comment
biz_audit_log：id, bill_id, action(1通过/2退回), comment, audit_by, audit_time
sys_user：user_id, user_name, nick_name, email, status, create_time
