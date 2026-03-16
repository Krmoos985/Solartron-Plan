package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.phase.custom.CustomPhaseCommand;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;

import java.util.List;

/**
 * 快速初始化分配阶段（Custom Phase）
 * <p>
 * 针对拥有级联影子变量的 @PlanningListVariable 模型，
 * 默认的 Construction Heuristic 会在长列表（如3000条记录）中尝试所有可能的插入位置，
 * 导致指数级的 $O(N^3)$ 测试开销，无法在短时间内完成初始化（分配）。
 * 本阶段替代默认的 CH，以 $O(N)$ 复杂度线性地将所有订单均衡、快速地追加到产线末尾。
 * </p>
 */
public class FastAssignInitPhase implements CustomPhaseCommand<MotherRollSchedule> {

    @Override
    public void changeWorkingSolution(ScoreDirector<MotherRollSchedule> scoreDirector) {
        MotherRollSchedule schedule = scoreDirector.getWorkingSolution();
        List<ProductionLine> lines = schedule.getProductionLines();
        List<MotherRollOrder> orders = schedule.getOrders();

        if (lines == null || lines.isEmpty() || orders == null || orders.isEmpty()) {
            return; // 无产线或无任务
        }

        // 简易的轮询产线游标
        int lineCount = lines.size();
        int curLineIdx = 0;

        for (MotherRollOrder order : orders) {
            // 已被外力（如增量求解、固定安排）固定的任务，不动
            if (order.getAssignedLine() != null || order.isPinned()) {
                continue;
            }

            // 寻找一条兼容该订单的产线
            ProductionLine selectedLine = null;
            for (int i = 0; i < lineCount; i++) {
                ProductionLine candidate = lines.get((curLineIdx + i) % lineCount);
                if (order.isCompatibleWith(candidate)) {
                    selectedLine = candidate;
                    curLineIdx = (curLineIdx + i + 1) % lineCount; // 为下一个选择流转游标
                    break;
                }
            }

            if (selectedLine != null) {
                int size = selectedLine.getOrders().size();

                // 必须在对底层集合修改前后精准上报状态给 Timefold
                // 1. 之前订单未分配，现在即将分配它，故抛弃其 unassigned 身份，挽救 init 惩罚
                scoreDirector.beforeListVariableElementAssigned(selectedLine, "orders", order);
                // 2. 将要在集合尾部插入它
                scoreDirector.beforeListVariableChanged(selectedLine, "orders", size, size);
                
                selectedLine.getOrders().add(order);

                // 3. 报告范围已扩增
                scoreDirector.afterListVariableChanged(selectedLine, "orders", size, size + 1);
                // 4. 正式宣告其成为 assigned 成员
                scoreDirector.afterListVariableElementAssigned(selectedLine, "orders", order);
                
                // 触发影子变量更新计算。由于只在末尾追加，不会引发灾难性的级联计算。
                scoreDirector.triggerVariableListeners();
            }
        }
    }
}
