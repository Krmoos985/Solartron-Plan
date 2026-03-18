package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import com.changyang.scheduling.rest.dto.ConstraintSelectionDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@ConstraintConfiguration
public class SchedulingConstraintConfiguration {

    @ConstraintWeight(SchedulingConstraintIds.HC1)
    private HardMediumSoftScore hc1ProductLineCompatibility = HardMediumSoftScore.ONE_HARD;

    @ConstraintWeight(SchedulingConstraintIds.HC2)
    private HardMediumSoftScore hc2UrgentInventoryFirst = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.HC3)
    private HardMediumSoftScore hc3ExceptionTimeConflict = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.HC4)
    private HardMediumSoftScore hc4ThicknessSinglePeak = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.MC1)
    private HardMediumSoftScore mc1FilterPriorityOrder = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.MC2)
    private HardMediumSoftScore mc2HighInventoryNoAdvance = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.SC1)
    private HardMediumSoftScore sc1MinimizeChangeover = HardMediumSoftScore.ONE_SOFT;

    @ConstraintWeight(SchedulingConstraintIds.SC2)
    private HardMediumSoftScore sc2InventoryPriority = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.SC3)
    private HardMediumSoftScore sc3ExpectedStartBias = HardMediumSoftScore.ZERO;

    @ConstraintWeight(SchedulingConstraintIds.SC4)
    private HardMediumSoftScore sc4PreferredLine = HardMediumSoftScore.ZERO;

    public static SchedulingConstraintConfiguration defaults() {
        return new SchedulingConstraintConfiguration();
    }

    public Map<String, HardMediumSoftScore> toConstraintWeightMap() {
        Map<String, HardMediumSoftScore> weights = new LinkedHashMap<>();
        weights.put("HC1", hc1ProductLineCompatibility);
        weights.put("HC2", hc2UrgentInventoryFirst);
        weights.put("HC3", hc3ExceptionTimeConflict);
        weights.put("HC4", hc4ThicknessSinglePeak);
        weights.put("MC1", mc1FilterPriorityOrder);
        weights.put("MC2", mc2HighInventoryNoAdvance);
        weights.put("SC1", sc1MinimizeChangeover);
        weights.put("SC2", sc2InventoryPriority);
        weights.put("SC3", sc3ExpectedStartBias);
        weights.put("SC4", sc4PreferredLine);
        return weights;
    }

    public List<String> describeEnabledConstraints() {
        List<String> enabled = new ArrayList<>();
        toConstraintWeightMap().forEach((code, weight) -> {
            if (!HardMediumSoftScore.ZERO.equals(weight)) {
                enabled.add(code + "=" + weight);
            }
        });
        return enabled;
    }

    public List<String> describeDisabledConstraints() {
        List<String> disabled = new ArrayList<>();
        toConstraintWeightMap().forEach((code, weight) -> {
            if (HardMediumSoftScore.ZERO.equals(weight)) {
                disabled.add(code);
            }
        });
        return disabled;
    }

    public static List<String> currentExcelModeConstraintCodes() {
        return List.of("HC1", "HC2", "HC3", "HC4", "MC1", "MC2", "SC1", "SC2", "SC3", "SC4");
    }

    public static List<String> splitLockedConstraintCodes() {
        return List.of("HC5", "SC5");
    }

    public static SchedulingConstraintConfiguration fromSelection(ConstraintSelectionDto selection) {
        SchedulingConstraintConfiguration config = defaults();
        if (selection == null) {
            return config;
        }

        config.setHc1ProductLineCompatibility(selection.isHc1() ? HardMediumSoftScore.ONE_HARD : HardMediumSoftScore.ZERO);
        config.setHc2UrgentInventoryFirst(selection.isHc2() ? HardMediumSoftScore.ONE_HARD : HardMediumSoftScore.ZERO);
        config.setHc3ExceptionTimeConflict(selection.isHc3() ? HardMediumSoftScore.ONE_HARD : HardMediumSoftScore.ZERO);
        config.setHc4ThicknessSinglePeak(selection.isHc4() ? HardMediumSoftScore.ONE_HARD : HardMediumSoftScore.ZERO);
        config.setMc1FilterPriorityOrder(selection.isMc1() ? HardMediumSoftScore.ONE_MEDIUM : HardMediumSoftScore.ZERO);
        config.setMc2HighInventoryNoAdvance(selection.isMc2() ? HardMediumSoftScore.ONE_MEDIUM : HardMediumSoftScore.ZERO);
        config.setSc1MinimizeChangeover(selection.isSc1() ? HardMediumSoftScore.ONE_SOFT : HardMediumSoftScore.ZERO);
        config.setSc2InventoryPriority(selection.isSc2() ? HardMediumSoftScore.ofSoft(2) : HardMediumSoftScore.ZERO);
        config.setSc3ExpectedStartBias(selection.isSc3() ? HardMediumSoftScore.ONE_SOFT : HardMediumSoftScore.ZERO);
        config.setSc4PreferredLine(selection.isSc4() ? HardMediumSoftScore.ONE_SOFT : HardMediumSoftScore.ZERO);
        return config;
    }
}
