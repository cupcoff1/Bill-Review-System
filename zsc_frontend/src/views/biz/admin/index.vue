<template>
  <div class="app-container dashboard">

    <el-row :gutter="20" class="dashboard-stats">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon total-icon"><el-icon :size="28"><User /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.userCount + stats.reviewerCount }}</div>
              <div class="stat-label">总用户</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon user-icon"><el-icon :size="28"><UserFilled /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.userCount }}</div>
              <div class="stat-label">普通用户</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon reviewer-icon"><el-icon :size="28"><Checked /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.reviewerCount }}</div>
              <div class="stat-label">审核员</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-stats">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon bill-icon"><el-icon :size="28"><Document /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalBills }}</div>
              <div class="stat-label">总票据</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon pending-icon"><el-icon :size="28"><Clock /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value pending-value">{{ stats.pendingBills }}</div>
              <div class="stat-label">待审核</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon approved-icon"><el-icon :size="28"><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value approved-value">{{ stats.approvedBills }}</div>
              <div class="stat-label">已通过</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon rejected-icon"><el-icon :size="28"><CircleClose /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value rejected-value">{{ stats.rejectedBills }}</div>
              <div class="stat-label">已退回</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-charts">
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>用户角色分布</span>
            </div>
          </template>
          <div ref="roleChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>票据状态概览</span>
            </div>
          </template>
          <div ref="billChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>快捷操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" icon="User" size="large" @click="goUserManage">用户管理</el-button>
            <el-button type="success" icon="Menu" size="large" @click="goCategoryManage">类别管理</el-button>
            <el-button type="warning" icon="Checked" size="large" @click="goRegisterReview">注册审核</el-button>
            <el-button type="info" icon="TrendCharts" size="large" @click="goAiAssistant">AI助手</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-tables">
      <el-col :span="24">
        <el-card class="table-card">
          <template #header>
            <div class="card-header">
              <span>待审批注册申请</span>
              <el-badge :value="registerRequests.length" :hidden="!registerRequests.length" />
            </div>
          </template>
          <el-table v-loading="loading" :data="registerRequests" style="width: 100%" max-height="340">
            <el-table-column label="邮箱" prop="email" min-width="180" :show-overflow-tooltip="true" />
            <el-table-column label="申请角色" prop="roleKey" width="110">
              <template #default="scope">
                <el-tag v-if="scope.row.roleKey === 'reviewer'" type="warning" size="small">审核员</el-tag>
                <el-tag v-else-if="scope.row.roleKey === 'user'" type="primary" size="small">普通用户</el-tag>
                <el-tag v-else size="small">{{ scope.row.roleKey }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请说明" prop="note" min-width="160" :show-overflow-tooltip="true" />
            <el-table-column label="申请时间" prop="createTime" width="160">
              <template #default="scope">
                <span>{{ parseTime(scope.row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="scope">
                <el-button link type="success" icon="Select" size="small" @click="handleApprove(scope.row)">通过</el-button>
                <el-button link type="danger" icon="CloseBold" size="small" @click="handleReject(scope.row)">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !registerRequests.length" description="暂无待审批的注册申请" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { ref, getCurrentInstance, onMounted, nextTick } from 'vue'
import { User, UserFilled, Checked, Document, Clock, CircleCheck, CircleClose, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import useSettingsStore from '@/store/modules/settings'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminStats, listRegisterRequests, approveRegisterRequest, rejectRegisterRequest } from '@/api/biz/admin'

const { proxy } = getCurrentInstance()
const settingsStore = useSettingsStore()
const router = useRouter()

const roleChartRef = ref(null)
const billChartRef = ref(null)
const loading = ref(false)

const stats = ref({ totalUsers: 0, userCount: 0, reviewerCount: 0, adminCount: 0, totalBills: 0, pendingBills: 0, approvedBills: 0, rejectedBills: 0 })
const registerRequests = ref([])

function parseTime(time) {
  if (!time) return '-'
  return proxy.parseTime(time)
}

function isDark() {
  return settingsStore.isDark
}

// ==================== 图表 ====================
function initRoleChart() {
  if (!roleChartRef.value) return
  const chart = echarts.init(roleChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle', textStyle: { color: dark ? '#ccc' : '#333' } },
    series: [{
      name: '用户角色', type: 'pie', radius: ['45%', '70%'], center: ['60%', '50%'],
      label: { color: dark ? '#ccc' : '#333' },
      data: [
        { value: stats.value.userCount, name: '普通用户', itemStyle: { color: '#4facfe' } },
        { value: stats.value.reviewerCount, name: '审核员', itemStyle: { color: '#43e97b' } }
      ].filter(d => d.value > 0),
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function initBillChart() {
  if (!billChartRef.value) return
  const chart = echarts.init(billChartRef.value)
  const dark = isDark()
  const s = stats.value
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle', textStyle: { color: dark ? '#ccc' : '#333' } },
    series: [{
      name: '票据状态', type: 'pie', radius: ['45%', '70%'], center: ['60%', '50%'],
      label: { color: dark ? '#ccc' : '#333' },
      data: [
        { value: s.pendingBills, name: '待审核', itemStyle: { color: '#e6a23c' } },
        { value: s.approvedBills, name: '已通过', itemStyle: { color: '#67c23a' } },
        { value: s.rejectedBills, name: '已退回', itemStyle: { color: '#f56c6c' } }
      ].filter(d => d.value > 0),
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

// ==================== 数据加载 ====================
function loadData() {
  loading.value = true
  getAdminStats().then(res => {
    stats.value = res.data
    nextTick(() => {
      initRoleChart()
      initBillChart()
    })
  })
  listRegisterRequests().then(res => {
    registerRequests.value = res.data || []
  }).finally(() => { loading.value = false })
}

// ==================== 注册审批 ====================
function handleApprove(row) {
  ElMessageBox.prompt('审批意见（可选）', '通过注册申请', {
    confirmButtonText: '确认通过',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '输入审批意见...'
  }).then(({ value }) => {
    approveRegisterRequest(row.id, value || '').then(() => {
      ElMessage.success('已通过')
      loadData()
    })
  }).catch(() => {})
}

function handleReject(row) {
  ElMessageBox.prompt('请输入拒绝原因', '拒绝注册申请', {
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '请输入拒绝原因...',
    inputValidator: (val) => val ? true : '拒绝原因不能为空'
  }).then(({ value }) => {
    rejectRegisterRequest(row.id, value).then(() => {
      ElMessage.success('已拒绝')
      loadData()
    })
  }).catch(() => {})
}

// ==================== 导航 ====================
function goUserManage() { router.push('/admin/users') }
function goCategoryManage() { router.push('/admin/bizCategory') }
function goRegisterReview() { router.push('/admin/registerReview') }
function goAiAssistant() { router.push('/admin/aiAssistant') }

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.dashboard { padding: 20px; }

.dashboard-stats {
  margin-bottom: 20px;
  .el-col { margin-bottom: 12px; }
  .stat-card {
    transition: all 0.3s;
    &:hover { transform: translateY(-3px); }
    .stat-content {
      display: flex; align-items: center; justify-content: space-around; padding: 6px 0;
      .stat-icon {
        width: 56px; height: 56px; border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        &.total-icon    { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }
        &.user-icon     { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); color: #fff; }
        &.reviewer-icon { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); color: #fff; }
        &.admin-icon    { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); color: #fff; }
        &.bill-icon     { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: #fff; }
        &.pending-icon  { background: linear-gradient(135deg, #f7971e 0%, #ffd200 100%); color: #fff; }
        &.approved-icon { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }
        &.rejected-icon { background: linear-gradient(135deg, #f56c6c 0%, #e6a23c 100%); color: #fff; }
      }
      .stat-info {
        text-align: center;
        .stat-value { font-size: 24px; font-weight: bold; color: var(--el-text-color-primary); margin-bottom: 2px; }
        .stat-value.pending-value { color: #e6a23c; }
        .stat-value.approved-value { color: #67c23a; }
        .stat-value.rejected-value { color: #f56c6c; }
        .stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
      }
    }
  }
}

.dashboard-charts {
  margin-bottom: 20px;
  .chart-card {
    height: 100%;
    .card-header { display: flex; justify-content: space-between; align-items: center; font-weight: bold; font-size: 16px; }
    .chart-container { height: 320px; width: 100%; }
  }
  .quick-actions {
    height: 320px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 16px;
  }
}

.dashboard-tables {
  .table-card {
    .card-header {
      display: flex; justify-content: space-between; align-items: center;
      font-weight: bold; font-size: 16px;
    }
  }
}

:deep(.el-card__header) { padding: 15px 20px; }
</style>
