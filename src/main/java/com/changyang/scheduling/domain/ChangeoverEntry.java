package com.changyang.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 换型时间输入矩阵 — Problem Fact
 * <p>
 * 从外部输入的原始换型数据。分为配方_型号维度和厚度维度。
 * 会在 ChangeoverService 内部转换为 HashMap 缓存。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeoverEntry {

    public enum Type {
        FORMULA_MODEL, // 配方+型号维度
        THICKNESS      // 厚度维度
    }

    /** 换型类型 */
    private Type type;

    // --- 配方+型号维度使用 ---
    private String fromFormulaCode;
    private String fromProductCode;
    private String toFormulaCode;
    private String toProductCode;

    // --- 厚度维度使用 ---
    private Integer fromThickness;
    private Integer toThickness;

    /** 切换所需分钟数 */
    private int changeoverMinutes;

    /**
     * 构建配方+型号维度的 key
     */
    public String buildFormulaModelKey() {
        return fromFormulaCode + "_" + fromProductCode + "->" + toFormulaCode + "_" + toProductCode;
    }

    /**
     * 构建厚度维度的 key
     */
    public String buildThicknessKey() {
        return fromThickness + "->" + toThickness;
    }
}
