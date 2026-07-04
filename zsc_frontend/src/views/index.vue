<template>
  <div class="app-container dashboard">

    <!-- ==================== 管理员(审核员)视图 ==================== -->
    <template v-if="isReviewer">
      <el-row :gutter="20" class="dashboard-stats">
        <el-col :xs="12" :sm="8" :md="span5">
          <el-card class="stat-card clickable-card" shadow="hover" @click="goReview">
            <div class="stat-content">
              <div class="stat-icon pending-icon">
                <el-icon :size="28"><Clock /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ reviewerStats.pendingCount }}</div>
                <div class="stat-label">待审核</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="span5">
          <el-card class="stat-card clickable-card" shadow="hover" @click="goTodayApproved">
            <div class="stat-content">
              <div class="stat-icon approved-icon">
                <el-icon :size="28"><CircleCheck /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ reviewerStats.todayApproved }}</div>
                <div class="stat-label">今日通过</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="span5">
          <el-card class="stat-card clickable-card" shadow="hover" @click="goTodayRejected">
            <div class="stat-content">
              <div class="stat-icon rejected-icon">
                <el-icon :size="28"><CircleClose /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ reviewerStats.todayRejected }}</div>
                <div class="stat-label">今日退回</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="span5">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon rate-icon">
                <el-icon :size="28"><TrendCharts /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ reviewerStats.approvalRate }}%</div>
                <div class="stat-label">通过率</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="span5">
          <el-card class="stat-card clickable-card" shadow="hover" @click="goStaleWarning">
            <div class="stat-content">
              <div class="stat-icon stale-icon">
                <el-icon :size="28"><WarningFilled /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value stale-value">{{ reviewerStats.stalePending }}</div>
                <div class="stat-label">积压预警 (&gt;3天)</div>
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
                <span>审核趋势分析</span>
              </div>
            </template>
            <div ref="reviewTrendChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="24" :md="12" :lg="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-header">
                <span>审核构成分析</span>
              </div>
            </template>
            <div ref="reviewCompositionChartRef" class="chart-container"></div>
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
              <el-button type="primary" icon="Checked" size="large" @click="goReview">去审核</el-button>
              <el-button type="success" icon="Document" size="large" @click="goBillManage">全部票据</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="dashboard-tables">
        <el-col :span="24">
          <el-card class="table-card">
            <template #header>
              <div class="card-header">
                <span>最近审核记录</span>
                <el-button type="primary" link @click="goReview">查看全部</el-button>
              </div>
            </template>
            <el-table v-loading="reviewerLoading" :data="reviewerStats.recentReviews" style="width: 100%" max-height="340">
              <el-table-column label="票据编号" prop="billNo" :show-overflow-tooltip="true" min-width="160" />
              <el-table-column label="标题" prop="title" :show-overflow-tooltip="true" min-width="180" />
              <el-table-column label="类别" prop="categoryName" width="100" />
              <el-table-column label="金额" prop="amount" width="120">
                <template #default="scope">
                  <span>{{ formatAmount(scope.row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="审核人" prop="auditBy" width="100" />
              <el-table-column label="结果" prop="status" width="80">
                <template #default="scope">
                  <dict-tag :options="biz_bill_status" :value="scope.row.status" />
                </template>
              </el-table-column>
              <el-table-column label="审核时间" prop="auditTime" width="160">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.auditTime) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- ==================== 普通用户视图 ==================== -->
    <template v-else>
      <el-row :gutter="20" class="dashboard-stats">
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon total-icon">
                <el-icon :size="28"><Document /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.total }}</div>
                <div class="stat-label">全部票据</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon draft-icon">
                <el-icon :size="28"><Edit /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.draft }}</div>
                <div class="stat-label">草稿中</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon pending-icon">
                <el-icon :size="28"><Clock /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.pending }}</div>
                <div class="stat-label">审批中</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon approved-icon">
                <el-icon :size="28"><CircleCheck /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.approved }}</div>
                <div class="stat-label">已通过</div>
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
                <span>票据状态分布</span>
              </div>
            </template>
            <div ref="statusChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="24" :md="12" :lg="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-header">
                <span>各类别金额汇总</span>
              </div>
            </template>
            <div ref="userCategoryChartRef" class="chart-container"></div>
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
              <el-button type="primary" icon="Plus" size="large" @click="goCreate">新增票据</el-button>
              <el-button type="success" icon="Document" size="large" @click="goMyBill">我的票据</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="dashboard-tables">
        <el-col :span="24">
          <el-card class="table-card">
            <template #header>
              <div class="card-header">
                <span>最近创建的票据</span>
                <el-button type="primary" link @click="goMyBill">查看全部</el-button>
              </div>
            </template>
            <el-table v-loading="loading" :data="recentBills" style="width: 100%" max-height="340">
              <el-table-column label="票据编号" prop="billNo" :show-overflow-tooltip="true" min-width="160" />
              <el-table-column label="标题" prop="title" :show-overflow-tooltip="true" min-width="180" />
              <el-table-column label="类别" prop="categoryName" width="100" />
              <el-table-column label="金额" prop="amount" width="120">
                <template #default="scope">
                  <span>{{ formatAmount(scope.row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" prop="status" width="90">
                <template #default="scope">
                  <dict-tag :options="biz_bill_status" :value="scope.row.status" />
                </template>
              </el-table-column>
              <el-table-column label="创建时间" prop="createTime" width="160">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup name="Index">
import { ref, computed, getCurrentInstance, onMounted, nextTick } from 'vue'
import { Document, Edit, Clock, CircleCheck, CircleClose, TrendCharts, WarningFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import useSettingsStore from '@/store/modules/settings'
import useUserStore from '@/store/modules/user'
import { listBill, getCategorySummary, getTrend, getReviewerStats, getReviewTrend, getReviewComposition } from '@/api/biz/bill'

const { proxy } = getCurrentInstance()
const settingsStore = useSettingsStore()
const userStore = useUserStore()
const { biz_bill_status } = proxy.useDict('biz_bill_status')
const router = useRouter()

const isReviewer = computed(() => userStore.roles.includes('reviewer'))
const span5 = 24 / 5

const statusChartRef = ref(null)
const categoryChartRef = ref(null)
const userCategoryChartRef = ref(null)
const trendChartRef = ref(null)
const reviewTrendChartRef = ref(null)
const reviewCompositionChartRef = ref(null)
const loading = ref(false)
const reviewerLoading = ref(false)

const statistics = ref({ total: 0, draft: 0, pending: 0, approved: 0, rejected: 0 })
const recentBills = ref([])

const reviewerStats = ref({
  pendingCount: 0,
  todayApproved: 0,
  todayRejected: 0,
  approvalRate: 0,
  stalePending: 0,
  recentReviews: []
})

function formatAmount(amount) {
  if (amount == null) return '-'
  return '¥' + Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function parseTime(time) {
  if (!time) return '-'
  return proxy.parseTime(time)
}

// ==================== 图表公共工具 ====================
function isDark() {
  return settingsStore.isDark
}

// ==================== 管理员图表 ====================
function initCategoryChart(data) {
  if (!categoryChartRef.value) return
  const chart = echarts.init(categoryChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>¥${p[0].value.toLocaleString()}` },
    grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '金额 (¥)', nameTextStyle: { color: dark ? '#ccc' : '#666' }, axisLabel: { color: dark ? '#aaa' : '#666' } },
    yAxis: { type: 'category', data: data.map(d => d.label), axisLabel: { color: dark ? '#ccc' : '#333', fontWeight: 'bold' } },
    series: [{
      name: '金额', type: 'bar', data: data.map(d => d.count),
      itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#409EFF' }, { offset: 1, color: '#67C23A' }]) },
      label: { show: true, position: 'right', color: dark ? '#ddd' : '#333', fontWeight: 'bold', formatter: p => '¥' + p.value.toLocaleString() }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function initTrendChart(data) {
  if (!trendChartRef.value) return
  const chart = echarts.init(trendChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.label), axisLabel: { color: dark ? '#aaa' : '#666' } },
    yAxis: { type: 'value', name: '提交数', nameTextStyle: { color: dark ? '#ccc' : '#666' }, axisLabel: { color: dark ? '#aaa' : '#666' } },
    series: [{
      name: '提交数', type: 'line', smooth: true, data: data.map(d => d.count),
      itemStyle: { color: '#409EFF' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64, 158, 255, 0.5)' }, { offset: 1, color: 'rgba(64, 158, 255, 0.01)' }]) }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

// ==================== 审核员新图表 ====================
function initReviewTrendChart(data) {
  if (!reviewTrendChartRef.value) return
  const chart = echarts.init(reviewTrendChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: p => {
        let s = p[0].name + '<br/>'
        p.forEach(item => {
          s += item.marker + ' ' + item.seriesName + ': ' + (item.seriesName === '通过率' ? item.value + '%' : item.value) + '<br/>'
        })
        return s
      }
    },
    legend: { data: ['审核单量', '通过率'], textStyle: { color: dark ? '#ccc' : '#333' } },
    grid: { left: '3%', right: '6%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.month), axisLabel: { color: dark ? '#aaa' : '#666', rotate: 30 } },
    yAxis: [
      { type: 'value', name: '单量', nameTextStyle: { color: dark ? '#ccc' : '#666' }, axisLabel: { color: dark ? '#aaa' : '#666' } },
      { type: 'value', name: '通过率(%)', nameTextStyle: { color: dark ? '#ccc' : '#666' }, axisLabel: { color: dark ? '#aaa' : '#666' }, max: 100, min: 0 }
    ],
    series: [
      {
        name: '审核单量', type: 'line', smooth: true, data: data.map(d => d.count),
        itemStyle: { color: '#4facfe' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(79, 172, 254, 0.4)' }, { offset: 1, color: 'rgba(79, 172, 254, 0.02)' }]) }
      },
      {
        name: '通过率', type: 'line', smooth: true, yAxisIndex: 1, data: data.map(d => d.passRate),
        itemStyle: { color: '#43e97b' },
        lineStyle: { type: 'dashed' }
      }
    ]
  })
  window.addEventListener('resize', () => chart.resize())
}

function initReviewCompositionChart(data) {
  if (!reviewCompositionChartRef.value) return
  const chart = echarts.init(reviewCompositionChartRef.value)
  const dark = isDark()
  const colors = ['#667eea', '#43e97b', '#f56c6c', '#4facfe', '#fa709a', '#e6a23c', '#f093fb', '#fee140']
  chart.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>¥${Number(p.value).toLocaleString()} (${p.percent}%)` },
    legend: { orient: 'vertical', left: 'left', top: 'middle', textStyle: { color: dark ? '#ccc' : '#333' } },
    color: colors,
    series: [{
      name: '费用构成', type: 'pie', radius: ['40%', '70%'], center: ['62%', '50%'],
      label: { color: dark ? '#ccc' : '#333', formatter: '{b}: {d}%' },
      data: data.map((d, i) => ({ value: d.amount, name: d.categoryName, itemStyle: { color: colors[i % colors.length] } })),
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

// ==================== 普通用户图表 ====================
function initStatusChart() {
  if (!statusChartRef.value) return
  const chart = echarts.init(statusChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle', textStyle: { color: dark ? '#ccc' : '#333' } },
    series: [{
      name: '票据状态', type: 'pie', radius: '60%', center: ['60%', '50%'],
      label: { color: dark ? '#ccc' : '#333' },
      data: [
        { value: statistics.value.draft, name: '草稿' },
        { value: statistics.value.pending, name: '审批中' },
        { value: statistics.value.approved, name: '已通过' },
        { value: statistics.value.rejected, name: '已退回' }
      ].filter(d => d.value > 0),
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function initUserCategoryChart(data) {
  if (!userCategoryChartRef.value) return
  const chart = echarts.init(userCategoryChartRef.value)
  const dark = isDark()
  chart.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>¥${p[0].value.toLocaleString()}` },
    grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '金额 (¥)', nameTextStyle: { color: dark ? '#ccc' : '#666' }, axisLabel: { color: dark ? '#aaa' : '#666' } },
    yAxis: { type: 'category', data: data.map(d => d.label), axisLabel: { color: dark ? '#ccc' : '#333', fontWeight: 'bold' } },
    series: [{
      name: '金额', type: 'bar', data: data.map(d => d.count),
      itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#409EFF' }, { offset: 1, color: '#67C23A' }]) },
      label: { show: true, position: 'right', color: dark ? '#ddd' : '#333', fontWeight: 'bold', formatter: p => '¥' + p.value.toLocaleString() }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

// ==================== 数据加载 ====================
function loadReviewerData() {
  reviewerLoading.value = true
  getReviewerStats().then(res => {
    reviewerStats.value = res.data
  }).finally(() => {
    reviewerLoading.value = false
    nextTick(() => {
      getReviewTrend().then(res => initReviewTrendChart(res.data || [])).catch(() => initReviewTrendChart([]))
      getReviewComposition().then(res => initReviewCompositionChart(res.data || [])).catch(() => initReviewCompositionChart([]))
    })
  })
}

function loadUserData() {
  loading.value = true
  listBill({ currentPage: 1, pageSize: 10 }).then(res => {
    const list = res.data.list || []
    recentBills.value = list.slice(0, 5)
    statistics.value.total = res.data.total || 0
    Promise.all([
      listBill({ currentPage: 1, pageSize: 1, status: '0' }),
      listBill({ currentPage: 1, pageSize: 1, status: '1' }),
      listBill({ currentPage: 1, pageSize: 1, status: '2' }),
      listBill({ currentPage: 1, pageSize: 1, status: '3' })
    ]).then(([draftRes, pendingRes, approvedRes, rejectedRes]) => {
      statistics.value.draft = draftRes.data.total || 0
      statistics.value.pending = pendingRes.data.total || 0
      statistics.value.approved = approvedRes.data.total || 0
      statistics.value.rejected = rejectedRes.data.total || 0
    }).finally(() => {
      nextTick(() => {
        initStatusChart()
        getCategorySummary().then(res => initUserCategoryChart(res.data || [])).catch(() => initUserCategoryChart([]))
      })
    })
  }).finally(() => { loading.value = false })
}

function goCreate() { router.push('/bill/myBill') }
function goMyBill() { router.push('/bill/myBill') }
function goReview() { router.push('/bill/review') }
function goBillManage() { router.push('/bill/manage') }
function goTodayApproved() { router.push('/bill/reviewer-detail?type=approved') }
function goTodayRejected() { router.push('/bill/reviewer-detail?type=rejected') }
function goStaleWarning() { router.push('/bill/review?staleOnly=1') }

onMounted(() => {
  if (isReviewer.value) {
    loadReviewerData()
  } else {
    loadUserData()
  }
})
</script>

<style scoped lang="scss">
.dashboard {
  padding: 20px;

  .dashboard-stats {
    margin-bottom: 20px;

    .el-col {
      margin-bottom: 12px;
    }

    .stat-card {
      transition: all 0.3s;
      &:hover { transform: translateY(-3px); }

      &.clickable-card {
        cursor: pointer;
        &:hover { transform: translateY(-3px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
        &:active { transform: translateY(-1px); }
      }

      .stat-content {
        display: flex;
        align-items: center;
        justify-content: space-around;
        padding: 6px 0;

        .stat-icon {
          width: 56px;
          height: 56px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;

          &.total-icon     { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }
          &.draft-icon     { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: #fff; }
          &.pending-icon   { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); color: #fff; }
          &.approved-icon  { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); color: #fff; }
          &.rejected-icon  { background: linear-gradient(135deg, #f7971e 0%, #ffd200 100%); color: #fff; }
          &.rate-icon      { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }
          &.stale-icon     { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); color: #fff; }
        }

        .stat-info {
          text-align: center;
          .stat-value { font-size: 24px; font-weight: bold; color: var(--el-text-color-primary); margin-bottom: 2px; }
          .stat-value.stale-value { color: #e6a23c; }
          .stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
        }
      }
    }
  }

  .dashboard-charts {
    margin-bottom: 20px;

    .chart-card {
      height: 100%;
      .chart-container { height: 320px; width: 100%; }
    }

    .quick-actions {
      height: 320px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 20px;
    }
  }

  .dashboard-tables {
    .table-card {
      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-weight: bold;
        font-size: 16px;
      }
    }
  }
}

:deep(.el-card__header) {
  padding: 15px 20px;
}
</style>
