package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MergedTask;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.ProductionLine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务合并后处理服务
 * <p>
 * 将 Solver 排出的日颗粒度子任务列表中，
 * 属于同母任务且连续的子任务合并还原为完整（或部分完整）的任务块。
 * </p>
 */
@Service
public class TaskMerger {

    /**
     * 合并单条产线上的连续同源子任务
     *
     * @param line 包含由 Solver 排序后的 orders 列表的产线
     * @return 合并后的任务块列表
     */
    public List<MergedTask> mergeTasks(ProductionLine line) {
        List<MergedTask> result = new ArrayList<>();
        List<MotherRollOrder> tasks = line.getOrders();

        if (tasks == null || tasks.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < tasks.size()) {
            MotherRollOrder current = tasks.get(i);
            int j = i + 1;

            // 向后寻找可以合并的连续子任务
            while (j < tasks.size()) {
                MotherRollOrder next = tasks.get(j);
                
                // 完全相同的父任务 ID 才可合并
                boolean sameParent = next.getParentTaskId() != null 
                        && current.getParentTaskId() != null 
                        && next.getParentTaskId().equals(current.getParentTaskId());
                
                // 必须是连续的日次（如 current 是 day=1，next 是 day=2）
                boolean consecutiveDays = next.getDayIndex() == current.getDayIndex() + (j - i);

                if (sameParent && consecutiveDays) {
                    j++;
                } else {
                    break;
                }
            }

            MergedTask merged = new MergedTask();
            String originalId = current.getParentTaskId() != null ? current.getParentTaskId() : current.getId();
            merged.setOriginalTaskId(originalId);
            merged.setLineId(line.getId());
            merged.setPlannedStart(tasks.get(i).getStartTime());
            merged.setPlannedEnd(tasks.get(j - 1).getEndTime());
            merged.setDaysCovered(j - i);
            merged.setTotalDays(current.getTotalDays() > 0 ? current.getTotalDays() : 1);

            result.add(merged);
            i = j; // 跳转到下一个未处理的任务
        }
        return result;
    }
}
