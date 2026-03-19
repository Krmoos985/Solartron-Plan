# 小样本约束逐步测试计划

更新时间：2026-03-18

## 目标

基于小样本数据集 [validation-workbook-small.xlsx](/d:/Github/Solartron-Plan/docs/validation-data/validation-workbook-small.xlsx)，按“一次只新增 1 条约束”的方式逐步恢复模型，定位哪一条约束或哪一类能力导致不可行。

当前小样本规模：

- 订单：`42`
- 产线：`2`
- 停机：`6`
- 过滤器计划：`4`
- holiday：`7`

## 当前结论

2026-03-18 的一次全开测试结果：

- 启用：`HC1 HC2 HC3 HC4 MC1 MC2 SC1 SC2 SC3 SC4`
- 结果：`-1352hard/-7medium/-723245soft`
- 分配：`L2=1`，`L4=41`

这说明：

- 小样本已经足够暴露问题
- 当前不能把 10 条约束一次性全开来判断模型状态
- 需要按层恢复

## 固定规则

每一轮都遵守下面 4 条：

1. 只在上一轮基础上新增 `1` 条约束
2. 所有轮次都以 `HC1 + SC1` 为基线
3. 只要出现 `hard != 0`，就停在这一轮分析，不继续叠加
4. 每轮都记录最终分数、第一次出现 `0hard` 的时间、两条产线分配数

## 当前不建议启用

### `HC3`

暂时不要作为有效验证项。

原因：

- 当前时间推导还不能主动“等待停机结束再开工”
- 任务时间仍然是前后直接 `plusMinutes`
- 相关代码见 [MotherRollOrder.java](/d:/Github/Solartron-Plan/src/main/java/com/changyang/scheduling/domain/MotherRollOrder.java#L143) 和 [FactoryCalendar.java](/d:/Github/Solartron-Plan/src/main/java/com/changyang/scheduling/domain/FactoryCalendar.java#L29)

结论：

- `HC3` 现在更像“已接入但暂不可用”
- 先不要放进逐轮恢复链路

### `HC5` / `SC5`

暂时也不要启用。

原因：

- 当前 Excel 模式还没有重新打开拆分链路
- `TaskSplitter` 代码还在，但 `preprocess` 没启用拆分
- 相关代码见 [SchedulingService.java](/d:/Github/Solartron-Plan/src/main/java/com/changyang/scheduling/service/SchedulingService.java#L41)

结论：

- 这两条要等“拆分模式单独恢复”后再测

## 推荐测试顺序

| 轮次 | 启用约束 | 目标 | 通过标准 | 失败时优先怀疑 |
| --- | --- | --- | --- | --- |
| 1 | `HC1 + SC1` | 建立稳定基线 | `0hard/0medium/...soft` | 基线链路、数据导入、换型逻辑 |
| 2 | `HC1 + SC1 + SC4` | 验证偏好产线只影响软分 | 仍然 `0hard` | 偏好线字段、偏好线约束 |
| 3 | `HC1 + SC1 + SC3` | 验证期望开始时间偏差 | 仍然 `0hard` | expectedStartTime 数据、时间偏差计算 |
| 4 | `HC1 + SC1 + SC2` | 验证低库存软排序 | 仍然 `0hard` | inventorySupplyDays、同线排序逻辑 |
| 5 | `HC1 + SC1 + MC2` | 验证高库存不过早排产 | 仍然 `0hard` | expectedStartTime、库存天数 |
| 6 | `HC1 + SC1 + HC4` | 验证厚度单峰 | `0hard` | HC4 实现、厚度样本顺序 |
| 7 | `HC1 + SC1 + MC1` | 验证过滤器优先顺序 | 仍然 `0hard` | synthetic priority rows、过滤器窗口逻辑 |
| 8 | `HC1 + SC1 + HC2` | 验证紧急库存硬优先 | `0hard` | HC2 是否过硬、排序表达方式 |
| 9 | 组合回归 | 把前面单独通过的约束逐步叠回去 | 每叠一条都保持 `0hard` | 约束之间的相互冲突 |

## 组合回归顺序

如果单条验证都通过，组合时按这个顺序叠加：

1. `HC1 + SC1`
2. `+ SC4`
3. `+ SC3`
4. `+ SC2`
5. `+ MC2`
6. `+ HC4`
7. `+ MC1`
8. `+ HC2`

说明：

- `HC2` 放最后，因为它是强排序 hard，最容易把问题推到不可行
- `HC3` 不进这条主链
- `HC5/SC5` 等拆分模式恢复后，另开一条专门测试链

## 拆分模式测试链

这一条先不跟当前主链混合。

恢复拆分后，单独按下面顺序测：

1. `HC1 + SC1 + HC5`
2. `HC1 + SC1 + HC5 + SC5`
3. 再看是否需要叠加 `HC4`

目标：

- 先证明拆分后子任务顺序合法
- 再证明拆分后的连续生产行为合理
- 不要一上来就把库存、停机、过滤器一起带进去

## 每轮记录模板

复制下面这一段，每轮填一份：

```md
### Round X

- 日期：
- 数据集：`validation-workbook-small.xlsx`
- 启用约束：
- 终止条件：
- 最终 score：
- 是否 `0hard`：
- 第一次出现 `0hard` 的时间：
- 产线分配：`L2=?`，`L4=?`
- 甘特图观察：
- 结论：
- 下一步：
```

## 推荐操作方式

### 前端

1. 启动后端
2. 启动前端
3. 点击“直接加载小样本验证数据”
4. 只勾当前轮次要测试的约束
5. 求解
6. 记录结果

### 后端日志重点看

- 启动时：
  - `Constraint model status on startup`
- 求解前：
  - `Effective constraint configuration`
- 求解中：
  - `Solve progress`
- 求解后：
  - `Score`
  - `Hard-score feasible`
  - `L2 / L4` 分配数

## 建议的第一轮

先跑：

- `HC1`
- `SC1`

其他全部关闭。

这一轮如果还不能稳定拿到 `0hard`，就不要继续分析别的约束，先把基线链路修稳。
