# 验证数据包

这个目录用于存放“基于现有 468 条真实订单扩展出来的验证数据”。目标不是替代原始生产数据，而是补齐当前 Excel 中缺失的数据关系，让模型里保留但暂未启用的功能也能逐步验证。

## 文件

- `validation-workbook.xlsx`
  - 由 `scripts/generate_validation_workbook.py` 生成
  - 放在 `docs/validation-data/`
  - 目前已经可以被 `ExcelDataLoader` 读取

## 当前 workbook 结构

### `订单_验证版`

- 基于现有 `468` 条真实订单
- 回填当前原表全空字段：
  - `当前库存(M2)`
  - `月发货量(M2)`
- 扩展当前原表过弱关系：
  - `兼容产线`
  - 不再全部等于 `线别`
  - 混入部分双线兼容样本
- 追加少量 synthetic 订单：
  - `MC1` 过滤器优先顺序缺失组合
  - `HC5/SC5` 长订单拆分候选

### `停机计划`

- 对应 `ExceptionTime`
- 当前共 `6` 行
- `ExcelDataLoader` 已接入

### `过滤器更换计划`

- 对应 `FilterChangePlan`
- 当前共 `4` 行
- `ExcelDataLoader` 已接入

### `工厂日历`

- 对应 `FactoryCalendar`
- 当前覆盖 `35` 天
- 非工作日当前会作为 `holiday` 读入模型
- 但排程时间推导本身还没有真正跳过非工作时段

### `约束覆盖矩阵`

- 列出每条约束当前状态
- 标明由哪个 sheet 支撑验证

### `统计摘要`

- 汇总增强版数据包的关键统计
- 包括订单数、双线兼容数、库存覆盖分层等

## 当前生成规则

### 库存相关

- 按周期分配目标库存覆盖天数：
  - `5 / 8 / 12 / 18 / 25 / 40 / 60`
- 再反推出：
  - `当前库存(M2)`
  - `月发货量(M2)`
- 因此可以稳定得到：
  - `<10 天` 的紧急订单
  - `>30 天` 的高库存订单

### 兼容关系

- `QDJY` 固定只兼容 `双拉2线`
- `EST/ESY` 的部分厚度扩展为双线兼容
- `FDX/FDY` 的部分薄规格扩展为双线兼容
- 其余订单保留单线兼容

### synthetic 补点

- 追加 `MC1` 所需但原始 468 行里覆盖不足的组合
- 追加超长订单，作为拆分与连续生产验证样本

## 生成方式

在仓库根目录执行：

```powershell
python scripts/generate_validation_workbook.py
```

生成结果输出到：

```text
docs/validation-data/validation-workbook.xlsx
```

## 当前接入状态

- `订单_验证版`：已接入
- `停机计划`：已接入 `ExceptionTime`
- `过滤器更换计划`：已接入 `FilterChangePlan`
- `工厂日历`：已接入 `FactoryCalendar`

注意：
- 这些数据已经能被后端读取，但并不代表对应约束已经全部启用。
- 这套数据的意义是“让模型可以被系统性验证”，不是“完全模拟真实生产现场”。
