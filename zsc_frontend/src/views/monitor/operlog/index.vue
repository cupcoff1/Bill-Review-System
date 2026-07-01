<template>
   <div class="app-container">
      <div class="search-bar">
         <el-select v-model="queryParams.title" placeholder="模块" clearable style="width: 115px">
            <el-option label="用户管理" value="用户管理" />
            <el-option label="票据管理" value="票据管理" />
            <el-option label="类别管理" value="业务类别管理" />
            <el-option label="个人信息" value="个人信息" />
            <el-option label="操作日志" value="操作日志" />
            <el-option label="注册申请" value="注册申请" />
         </el-select>
         <el-input v-model="queryParams.operName" placeholder="操作人" clearable style="width: 110px" @keyup.enter="handleQuery" />
         <el-select v-model="queryParams.businessType" placeholder="类型" clearable style="width: 95px">
            <el-option v-for="dict in sys_oper_type" :key="dict.value" :label="dict.label" :value="dict.value" />
         </el-select>
         <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 85px">
            <el-option v-for="dict in sys_common_status" :key="dict.value" :label="dict.label" :value="dict.value" />
         </el-select>
         <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD HH:mm:ss" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束" style="width: 230px" :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]" />
         <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
         <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['monitor:operlog:remove']">删除</el-button>
         <el-button type="danger" plain icon="Delete" @click="handleClean" v-hasPermi="['monitor:operlog:remove']">清空</el-button>
         <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['monitor:operlog:export']">导出</el-button>
         <el-divider direction="vertical" />
         <span style="font-size:12px;color:#909399;white-space:nowrap">自动导出</span>
         <el-input-number v-model="exportInterval" :min="0" :max="1440" :step="5" size="small" style="width:90px" @change="handleSetInterval" v-hasPermi="['biz:admin:list']" />
         <span style="font-size:12px;color:#909399">分钟(0=关)</span>
      </div>

      <el-table ref="operlogRef" v-loading="loading" :data="operlogList" @selection-change="handleSelectionChange" :default-sort="defaultSort" @sort-change="handleSortChange">
         <el-table-column type="selection" width="50" align="center" />
         <el-table-column label="日志编号" align="center" prop="operId" width="100" />
         <el-table-column label="系统模块" align="center" prop="title" min-width="120" :show-overflow-tooltip="true" />
         <el-table-column label="操作类型" align="center" width="110">
            <template #default="scope">
               <dict-tag :options="sys_oper_type" :value="scope.row.businessType" />
            </template>
         </el-table-column>
         <el-table-column label="操作人员" align="center" prop="operName" min-width="110" :show-overflow-tooltip="true" />
         <el-table-column label="操作状态" align="center" width="90">
            <template #default="scope">
               <dict-tag :options="sys_common_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="操作日期" align="center" prop="operTime" width="170" sortable="custom">
            <template #default="scope">
               <span>{{ parseTime(scope.row.operTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="耗时" align="center" prop="costTime" width="100" sortable="custom">
            <template #default="scope">
               <span>{{ scope.row.costTime }}ms</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="85" fixed="right">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['monitor:operlog:query']">详细</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <!-- 操作日志详细 -->
      <el-dialog title="操作日志详细" v-model="open" width="800px" append-to-body>
         <el-form :model="form" label-width="100px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="操作模块：">{{ form.title }} / {{ typeFormat(form) }}</el-form-item>
                  <el-form-item
                    label="登录信息："
                  >{{ form.operName }} / {{ form.operIp }} / {{ form.operLocation }}</el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="请求地址：">{{ form.operUrl }}</el-form-item>
                  <el-form-item label="请求方式：">{{ form.requestMethod }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="操作方法：">{{ form.method }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="请求参数：" style="word-break: break-all; white-space: pre-wrap;">{{ form.operParam }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="返回参数：">{{ form.jsonResult }}</el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="操作状态：">
                     <div v-if="form.status === 0">正常</div>
                     <div v-else-if="form.status === 1">失败</div>
                  </el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="消耗时间：">{{ form.costTime }}毫秒</el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="操作时间：">{{ parseTime(form.operTime) }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="异常信息：" v-if="form.status === 1">{{ form.errorMsg }}</el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="open = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Operlog">
import { list, delOperlog, cleanOperlog } from "@/api/monitor/operlog"
import request from "@/utils/request"

const { proxy } = getCurrentInstance()
const { sys_oper_type, sys_common_status } = proxy.useDict("sys_oper_type", "sys_common_status")

const operlogList = ref([])
const open = ref(false)
const loading = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const defaultSort = ref({ prop: "operTime", order: "descending" })
const exportInterval = ref(0)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined
  }
})

const { queryParams, form } = toRefs(data)

/** 查询登录日志 */
function getList() {
  loading.value = true
  const params = proxy.addDateRange(queryParams.value, dateRange.value)
  // 清理所有空字符串参数，避免后端 Integer 绑定报错
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  list(params).then(response => {
    operlogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 操作日志类型字典翻译 */
function typeFormat(row, column) {
  return proxy.selectDictLabel(sys_oper_type.value, row.businessType)
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  queryParams.value.title = undefined
  queryParams.value.operName = undefined
  queryParams.value.businessType = undefined
  queryParams.value.status = undefined
  queryParams.value.pageNum = 1
  getList()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operId)
  multiple.value = !selection.length
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop
  queryParams.value.isAsc = column.order
  getList()
}

/** 详细按钮操作 */
function handleView(row) {
  open.value = true
  form.value = row
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operIds = row.operId || ids.value
  proxy.$modal.confirm('是否确认删除日志编号为"' + operIds + '"的数据项?').then(function () {
    return delOperlog(operIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有操作日志数据项?").then(function () {
    return cleanOperlog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/operlog/export",{
    ...queryParams.value,
  }, `config_${new Date().getTime()}.xlsx`)
}

function loadExportInterval() {
  request({ url: '/api/admin/operlog-export-interval', method: 'get' }).then(res => {
    exportInterval.value = res.data || 0
  }).catch(() => {})
}

function handleSetInterval(val) {
  request({ url: '/api/admin/operlog-export-interval', method: 'put', data: { interval: val } }).then(res => {
    proxy.$modal.msgSuccess('自动导出间隔已更新')
  })
}

getList()
loadExportInterval()
</script>

<style scoped>
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
