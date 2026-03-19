# Small Validation Workbook

文件：`validation-workbook-small.xlsx`

这是一份面向“逐条恢复约束和代码”的小样本验证数据，不是生产数据替代品。

## 目标

- 将订单规模压到 `42` 条，保持在 `30-50` 的目标范围内
- 保留两条产线
- 保留库存层级、双线兼容、停机、过滤器计划、工厂日历
- 保留 `MC1` synthetic 行和长订单，方便后续恢复 `HC5/SC5`

## 当前覆盖

- `HC1`: 单线与双线兼容订单
- `HC2 / SC2`: 紧急库存与不同库存覆盖层级
- `HC3`: 停机窗口
- `HC4 / SC1`: 多产品、多厚度与换型路径
- `MC1`: synthetic priority rows
- `MC2`: 高库存订单
- `SC4`: 偏好产线与兼容产线分离
- `HC5 / SC5`: 仅作为拆分候选样本，当前模式仍未启用

## 生成方式

在仓库根目录执行：

```powershell
python scripts/generate_small_validation_workbook.py
```

输出文件：

```text
docs/validation-data/validation-workbook-small.xlsx
```
