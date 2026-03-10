package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MotherRollOrder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务拆分预处理服务
 * <p>
 * 对于生产时长超过 1 天（24小时）的母卷任务，
 * 按照日颗粒度进行拆分，以适应 Timefold Solver 对换型和跨天连续性的精细排程要求。
 * </p>
 */
@Service
public class TaskSplitter {

    /**
     * 将原始任务列表拆分为求解所需的子任务列表
     *
     * @param originalTasks 原始未拆分的母卷订单列表
     * @return 拆分后的子订单列表
     */
    public List<MotherRollOrder> splitTasks(List<MotherRollOrder> originalTasks) {
        List<MotherRollOrder> result = new ArrayList<>();

        for (MotherRollOrder original : originalTasks) {
            int totalDays = (int) Math.ceil(original.getProductionDurationHours() / 24.0);

            if (totalDays <= 1) {
                original.setSplit(false);
                original.setTotalDays(1);
                original.setDayIndex(1);
                result.add(original);
            } else {
                double dailyHours = original.getProductionDurationHours() / totalDays;
                double dailyQuantity = original.getQuantity() / totalDays;

                for (int day = 1; day <= totalDays; day++) {
                    MotherRollOrder sub = new MotherRollOrder();
                    sub.setId(original.getId() + "-" + day);
                    sub.setMaterialCode(original.getMaterialCode());
                    sub.setProductCode(original.getProductCode());
                    sub.setFormulaCode(original.getFormulaCode());
                    sub.setThickness(original.getThickness());
                    sub.setCurrentInventory(original.getCurrentInventory());
                    sub.setMonthlyShipment(original.getMonthlyShipment());
                    sub.setExpectedStartTime(original.getExpectedStartTime());
                    sub.setCompatibleLines(original.getCompatibleLines());
                    sub.setPreferredLineCode(original.getPreferredLineCode());

                    // 拆分特性
                    sub.setParentTaskId(original.getId());
                    sub.setDayIndex(day);
                    sub.setTotalDays(totalDays);
                    sub.setSplit(true);
                    sub.setPinned(original.isPinned());

                    // 均摊时长和数量
                    sub.setProductionDurationHours(dailyHours);
                    sub.setQuantity(dailyQuantity);

                    result.add(sub);
                }
            }
        }
        return result;
    }
}
