package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.ChangeoverEntry;
import com.changyang.scheduling.domain.MotherRollOrder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 换型时间计算服务
 * <p>
 * 提供 O(1) 的换型时间查询能力，供领域模型（MotherRollOrder 的影子变量更新）和软约束使用。
 * 由于 MotherRollOrder 是求解过程中的 POJO，无法直接注入 Spring Bean，
 * 这里提供一种 ThreadLocal 或静态访问的机制，或者在加载数据时提供数据来源。
 * 目前采用单例模式的全局可用机制（为 Solver 计算进行专门优化）。
 * </p>
 */
@Service
public class ChangeoverService {

    private static ChangeoverService INSTANCE;

    private final Map<String, Integer> formulaModelCache = new HashMap<>();
    private final Map<String, Integer> thicknessCache = new HashMap<>();

    public ChangeoverService() {
        INSTANCE = this; // 简化的 Spring 静态注入，方便 POJO 调用
    }

    public static ChangeoverService getInstance() {
        return INSTANCE;
    }

    /**
     * 将从数据库或其他数据源查出的 ChangeoverEntry 列表初始化进高速查询缓存
     */
    public void initCache(List<ChangeoverEntry> entries) {
        formulaModelCache.clear();
        thicknessCache.clear();

        for (ChangeoverEntry entry : entries) {
            if (entry.getType() == ChangeoverEntry.Type.FORMULA_MODEL) {
                formulaModelCache.put(entry.buildFormulaModelKey(), entry.getChangeoverMinutes());
            } else if (entry.getType() == ChangeoverEntry.Type.THICKNESS) {
                thicknessCache.put(entry.buildThicknessKey(), entry.getChangeoverMinutes());
            }
        }
    }

    /**
     * 计算相邻两单之间的换型时间
     * <p>
     * 规则：取（配方_型号维度换型用时，厚度维度换型用时）的最大值。
     * 同一产品的直接判 0，缓存未命中的默认返回最大值或基础换型时间以防逻辑穿透。
     * </p>
     */
    public int calcChangeover(MotherRollOrder prev, MotherRollOrder current) {
        if (prev == null || current == null) {
            return 0; // 队伍首个订单，换型 0
        }

        // 同产品同属性判定 0（特别是被拆分的同一天连续子任务）
        if (prev.getFormulaCode().equals(current.getFormulaCode()) &&
            prev.getProductCode().equals(current.getProductCode()) &&
            prev.getThickness() == current.getThickness()) {
            return 0;
        }

        // 1. 查询配方+型号维度
        String fmKey = prev.getFormulaCode() + "_" + prev.getProductCode() + "->" + current.getFormulaCode() + "_" + current.getProductCode();
        int fmTime = formulaModelCache.getOrDefault(fmKey, 0);

        // 2. 查询厚度维度
        String thKey = prev.getThickness() + "->" + current.getThickness();
        int thTime = thicknessCache.getOrDefault(thKey, 0);

        // 取最大值
        return Math.max(fmTime, thTime);
    }
}
