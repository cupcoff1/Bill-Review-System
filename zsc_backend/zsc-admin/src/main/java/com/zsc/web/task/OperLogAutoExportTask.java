package com.zsc.web.task;

import com.zsc.common.utils.spring.SpringUtils;
import com.zsc.system.service.ISysOperLogService;
import com.zsc.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志自动导出定时任务
 * 每隔 N 分钟自动导出一份操作日志为 Excel 并清空当前日志
 * 间隔时间通过 sys_config 表 sys.operlog.autoExportInterval 配置（分钟，0=关闭）
 */
@Component
public class OperLogAutoExportTask {

    private static final Logger log = LoggerFactory.getLogger(OperLogAutoExportTask.class);

    /** 每30秒检查一次是否需要导出 */
    @Scheduled(fixedDelay = 30_000)
    public void checkAndExport() {
        try {
            ISysConfigService configService = SpringUtils.getBean(ISysConfigService.class);
            String intervalStr = configService.selectConfigByKey("sys.operlog.autoExportInterval");
            if (intervalStr == null || intervalStr.isEmpty() || "0".equals(intervalStr)) {
                return;
            }

            int intervalMinutes = Integer.parseInt(intervalStr);
            long currentTime = System.currentTimeMillis();

            // 用 Redis 记录上次导出时间，避免重复导出
            String lastExportKey = "operlog_last_export";
            com.zsc.common.core.redis.RedisCache redis = SpringUtils.getBean(com.zsc.common.core.redis.RedisCache.class);
            Long lastExport = redis.getCacheObject(lastExportKey);
            if (lastExport != null && (currentTime - lastExport) < intervalMinutes * 60_000L) {
                return;
            }

            // 导出操作日志
            ISysOperLogService operLogService = SpringUtils.getBean(ISysOperLogService.class);
            String filePath = exportOperLog(operLogService, intervalMinutes);

            // 清空日志
            operLogService.cleanOperLog();

            // 记录导出时间
            redis.setCacheObject(lastExportKey, currentTime);

            log.info("操作日志自动导出完成: {}, 间隔={}分钟", filePath, intervalMinutes);
        } catch (Exception e) {
            log.error("操作日志自动导出失败", e);
        }
    }

    private String exportOperLog(ISysOperLogService operLogService, int intervalMinutes) {
        String userDir = System.getProperty("user.dir");
        String dir = userDir + java.io.File.separator + "exports" + java.io.File.separator;
        java.io.File d = new java.io.File(dir);
        if (!d.exists()) d.mkdirs();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = "operlog_" + sdf.format(new java.util.Date()) + ".xlsx";
        String filePath = dir + fileName;

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath)) {
            java.util.List<com.zsc.system.domain.SysOperLog> list = operLogService.selectOperLogList(null, new com.zsc.system.domain.SysOperLog());

            // 用 Apache POI 直接写
            org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("操作日志");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {"编号", "模块", "类型", "操作人", "IP", "状态", "请求URL", "耗时(ms)", "操作时间"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (com.zsc.system.domain.SysOperLog log : list) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(log.getOperId() != null ? log.getOperId() : 0);
                row.createCell(1).setCellValue(log.getTitle() != null ? log.getTitle() : "");
                row.createCell(2).setCellValue(log.getBusinessType() != null ? log.getBusinessType() : 0);
                row.createCell(3).setCellValue(log.getOperName() != null ? log.getOperName() : "");
                row.createCell(4).setCellValue(log.getOperIp() != null ? log.getOperIp() : "");
                row.createCell(5).setCellValue(log.getStatus() != null ? log.getStatus() : 0);
                row.createCell(6).setCellValue(log.getOperUrl() != null ? log.getOperUrl() : "");
                row.createCell(7).setCellValue(log.getCostTime() != null ? log.getCostTime() : 0L);
                row.createCell(8).setCellValue(log.getOperTime() != null ? log.getOperTime().toString() : "");
            }
            wb.write(fos);
            wb.close();
        } catch (Exception e) {
            log.error("导出文件失败: {}", filePath, e);
        }
        return filePath;
    }
}
