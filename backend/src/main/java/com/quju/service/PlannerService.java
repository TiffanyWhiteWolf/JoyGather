package com.quju.service;

import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class PlannerService {
    public PlannerResponse generate(PlannerRequest request) {
        return new PlannerResponse(
                "月光底片｜老街夜游摄影漫步",
                "用镜头收集老街的灯光与路人，不比器材，只交换观察城市的方式。",
                Arrays.asList("城市探索", "摄影", request.getStyle() == null ? "轻松社交" : request.getStyle()),
                Arrays.asList("19:00 集合签到与破冰", "19:20 抽取摄影任务卡", "20:40 咖啡馆分享照片", "21:30 合影结束"),
                "发布前请确认集合地点、夜间照明与紧急联系人信息。"
        );
    }
}
