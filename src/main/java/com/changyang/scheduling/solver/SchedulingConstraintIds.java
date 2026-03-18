package com.changyang.scheduling.solver;

import java.util.Set;

public final class SchedulingConstraintIds {

    public static final String HC1 = "HC1-产品产线兼容";
    public static final String HC2 = "HC2-紧急库存优先";
    public static final String HC3 = "HC3-停机冲突";
    public static final String HC4 = "HC4-厚度单峰波浪";
    public static final String MC1 = "MC1-过滤器后20天优先顺序";
    public static final String MC2 = "MC2-高库存不前移";
    public static final String SC1 = "SC1-换型最小化";
    public static final String SC2 = "SC2-库存天数优先级";
    public static final String SC3 = "SC3-期望时间偏差";
    public static final String SC4 = "SC4-特定产线偏好";
    public static final String HC5 = "HC5-子任务保序";
    public static final String SC5 = "SC5-拆分子任务连续生产";

    public static final Set<String> CURRENT_DATA_AVAILABLE = Set.of(
            HC1, HC2, HC3, HC4, MC1, MC2, SC1, SC2, SC3, SC4
    );

    public static final Set<String> SPLIT_REQUIRED = Set.of(HC5, SC5);

    public static final Set<String> MC1_PRIORITY_KEYS = Set.of(
            "EST_19", "FDX_4", "FDY_4", "FDX_5", "FDX_7", "DJX_24", "EST_9"
    );

    private SchedulingConstraintIds() {
    }
}
