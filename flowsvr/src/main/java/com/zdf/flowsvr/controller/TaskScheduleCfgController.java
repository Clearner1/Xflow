package com.zdf.flowsvr.controller;

import com.zdf.flowsvr.constant.ErrorStatusReturn;
import com.zdf.flowsvr.data.ReturnStatus;
import com.zdf.flowsvr.data.ScheduleConfig;
import com.zdf.flowsvr.service.ScheduleConfigService;
import com.zdf.flowsvr.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task_schedule_cfg")
public class TaskScheduleCfgController {
    Logger logger = LoggerFactory.getLogger(TaskScheduleCfgController.class);

    @Autowired
    private ScheduleConfigService scheduleConfigService;

    /**
     * 获取任务类型配置列表
     * 
     * @return 任务类型配置列表
     */
    @GetMapping("list")
    public ReturnStatus getTaskTypeCfgList() {
        return scheduleConfigService.getTaskTypeCfgList();
    }

    /**
     * 设置任务配置
     * 
     * @param scheduleConfig 任务配置
     * @return 保存到数据库
     */
    @GetMapping("task_configuration")
    public ReturnStatus SetTaskCFG(@RequestBody ScheduleConfig scheduleConfig) {
        if (Utils.isStrNull(scheduleConfig.getTask_type())) {
            logger.error("input invalid");
            return ErrorStatusReturn.ERR_INPUT_INVALID;
        }
        return scheduleConfigService.save(scheduleConfig);
    }
}
