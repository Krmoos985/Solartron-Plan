### 一、业务问题描述

#### 1.1 要解决什么问题

> 长阳双拉车间目前有 2 条产线纳入 APS 排程范围，每个月需要生产若干种母卷产品。计划部门根据月度预测确定了"这个月要生产哪些型号的母卷、每个型号生产多少"。

现在的问题是：**这些母卷任务应该放到哪条产线上生产？在同一条产线上，先做哪个后做哪个？**

这个问题目前靠计划员用 Excel 手工排，依赖个人经验。我们要用算法自动求解。

#### 1.2 问题难点

难在"换型"。产线从生产一个型号切换到另一个型号需要时间（换配方、调厚度等）。不同产品之间的切换时间差异很大——配方相同只换厚度可能只要几十分钟，配方完全不同可能要几个小时。

如果乱排，换型时间总和会非常大，浪费大量产能。如果完全按换型最优排，可能库存快断货的产品迟迟排不上。

所以核心是在**库存紧急度**和**换型效率**之间找最优平衡。

#### 1.3 排程的基本特征

- 月度排程，不是日计划
- 不考虑交期（母卷没有直接客户交期）
- 不考虑齐套（不检查 BOM 物料是否到位）
- 排出来的是每条产线上的母卷生产顺序和时间档期
- 母卷任务按天拆分为日颗粒度子任务进行排程，排完后将连续的同产品子任务合并还原

---

### 二、决策变量

对于每一个日颗粒度的子任务，Solver 需要决定两件事：

1. **放到哪条产线**（从 2 条产线中选一条）
2. **在这条产线上排第几个**（确定生产顺序）

这两件事确定后，每个任务的计划开始时间、计划结束时间、与前一个任务的换型时间都可以自动推算出来。

---

### 三、当前排产面临的核心挑战

在手工排产过程中，计划员需要同时应对三个相互制约的难题。这些就是我们系统必须处理的核心约束来源。

#### 3.1 厚度轮转

产线生产不同厚度的产品时，工艺上要求厚度变化必须是平稳连续的——先从薄到厚，再从厚到薄，形成一个"波浪"。不能出现"薄-厚-薄-厚"的来回跳变，否则会导致产品质量问题（膜面瑕疵、厚度不均等）。

这意味着排序不是随意的，**厚度方向有硬性的工艺约束**。计划员必须把相近厚度的产品安排在一起，且厚度变化方向不能来回反转。

#### 3.2 库存压力

每个型号的母卷都有对应的下游分卷需求在消耗库存。有些型号库存快见底了（比如只够卖几天），有些型号库存还很充裕（够卖一两个月）。

计划员必须在排序时考虑**哪些型号最紧急、必须先做**。但紧急的型号不一定和当前产线上正在做的产品配方接近，强行提前会导致一次昂贵的换型。库存紧急度和换型效率经常互相矛盾。

#### 3.3 换型成本

每次从一个产品切换到另一个产品，都需要换型时间。换型时间取决于两个产品之间的差异程度：

- **配方和型号维度**：配方不同、型号不同，换型时间长
- **厚度维度**：厚度跨度大，换型时间长
- 实际换型时间取两个维度的最大值

计划员希望把配方相同、型号相近、厚度接近的产品排在一起，减少总换型时间。但这又可能违反库存优先级——配方一样的产品可能库存都很充裕，没必要现在做。

此外，一个母卷任务可能需要连续生产多天。如果被打断，存在两种成本：

- **换型插入**：中间插了一个不同产品，产生两次额外换型（当前产品→插入产品、插入产品→当前产品）
- **跨产线中断**：子任务分散到不同产线，每次在新产线开机都有很高的开机成本

因此业务导向是**尽可能连续生产、尽可能不换型**。

#### 3.4 三者的矛盾关系

这三个因素互相制约：

- **想减少换型** → 把相似产品排一起 → 但库存紧急的产品可能完全不同，插进来就破坏换型连续性
- **想满足库存紧急** → 紧急的排最前面 → 但厚度可能跳变，违反工艺要求
- **想满足厚度轮转** → 厚度平稳过渡 → 但过渡过程中可能跳过了紧急的产品

人工排产时，计划员只能凭经验在三者之间找一个大致可接受的方案。面对上百个任务时，人工很难找到全局最优。这就是为什么我们需要算法求解——在严格满足工艺硬约束的前提下，自动寻找库存安全和换型效率的最佳平衡点。

**这三个挑战直接对应了后续约束设计中的核心约束：
厚度轮转 → HC4
库存压力 → HC2/MC2/SC2
换型成本 → SC1/SC5。**

---

### 四、约束

基于上述三个核心挑战，加上产线设备限制、过滤器更换等实际生产条件，以及按日拆分带来的连续性要求，我们建立完整的约束体系。

#### 4.1 硬约束（绝对不可违反，违反则方案不可行）

**HC1：产品与产线的对应关系**

不是所有产品都能在所有产线上生产。每个型号有一个"允许的产线列表"，任务只能被分配到列表内的产线。这是设备工艺决定的物理约束。

**HC2：库存可供应天数 < 10天的任务必须优先排产**

来源于 3.2 库存压力。如果一个型号的库存只够卖不到10天了，该任务的第一天子任务必须排在最前面。不能因为换型方便就把它推到后面。只要第一天开始生产了，库存就在补充，后续子任务不需要强制最前。

**HC3：不能在停机时段排产**

两类停机时间：

- 过滤器更换期间的停机时段
- 例外时间（设备故障维修等）

任务的生产时间段不能和这些停机时段重叠。

**HC4：厚度轮转约束**

来源于 3.1 厚度轮转。同一条产线上的生产顺序，厚度必须遵循"由薄到厚"再"由厚到薄"的平稳连续模式。不允许出现"薄-厚-薄-厚"这种来回跳变。这是工艺要求，来回跳变会导致产品质量问题。

**HC5：同产线内子任务保序**

同一母任务的子任务如果被分配到同一条产线上，必须保持天数的先后顺序。第1天必须排在第2天前面，第2天必须排在第3天前面。跨产线的子任务不约束相对顺序。

#### 4.2 中等约束（强烈期望满足，极端情况可妥协）

**MC1：过滤器更换后20天内的排产顺序**

换完过滤器后，设备处于最佳工艺状态。这20天内必须按以下固定型号优先级排产：

```
T19EST(T)厚度188（高光）
  → T4FDX = T4FDY（两者共存时按厚到薄排）
    → T5FDX → T7FDX → T24DJX
      → T9EST厚度100（注意：不能衔接188、255）
        → T61 → T14
```

20天后可打破此规则。如果这些型号在20天内就能全部生产完，也可提前打破。

为什么不是硬约束：蓝图明确说有例外条件可以打破，不是绝对的。

**MC2：库存可供应天数 > 30天的任务不往前优化**

来源于 3.2 库存压力的反面。库存够用30天以上的产品不着急。不允许因为换型方便（比如和前面的产品配方一样）就把它提前安排生产。提前生产会占用产能、增加在库成本，没有业务价值。

#### 4.3 软约束（优化目标，尽可能满足）

**SC1：换型时间总和最小化**

来源于 3.3 换型成本。核心优化目标。相邻两个任务的换型时间计算方式：

```
换型时间 = max(配方_型号维度的切换用时, 厚度维度的切换用时)
```

两个维度分别查对应的换型时间表，取最大值。同配方同型号同厚度的话换型时间为0。

**SC2：库存可供应天数优先级（重要性高于 SC1）**

来源于 3.2 和 3.4 的矛盾权衡。在10-30天的正常区间内，库存天数短的应该优先排产。但不是绝对的——如果把一个库存15天的任务稍微往后挪一个位置，能让换型时间减少很多，那是值得的。

**SC3：期望生产时间偏差（重要性低于 SC1）**

每个母卷任务根据库存天数计算出一个"期望开始生产时间"。实际排出的时间和期望时间的偏差越小越好。

**SC4：产线偏好（重要性最低）**

某些型号有优先产线偏好（QDJY优先二线，ESYH优先四线）。能满足就满足，满足不了也没关系。

**SC5：尽可能连续生产（重要性最高的软约束）**

来源于 3.3 的连续性要求。同一母任务的子任务应尽可能在同一条产线上连续排列，不被其他任务打断。打断会产生成本，跨产线的打断成本远高于同产线内的插队：

- **同产线内插队**：产生两次额外换型时间（当前产品→插入产品、插入产品→当前产品）
- **跨产线中断**：新产线有很高的开机成本

这是所有软约束中权重最高的，体现"尽可能连续、尽可能不换型"的核心业务导向。但低于硬约束，确保库存<10天的紧急任务仍然可以插队。

#### 4.4 库存可供应天数三区间规则

|区间|约束级别|行为|
|---|---|---|
|< 10天|硬约束|紧急，必须优先排产，不可被换型优化推迟|
|10 ~ 30天|软约束（高权重）|正常区间，天数短的优先排，可参与换型优化权衡|
|> 30天|中等约束|不急，不往前优化换型合并（不因换型方便而提前生产）|

#### 4.5 约束优先级总结
```
绝对优先：    HC1, HC2, HC3, HC4, HC5（不可违反）
强烈期望：    MC1, MC2（极端情况可妥协）
优化目标：    SC5 > SC2 > SC1 > SC3 > SC4（按重要性排序）
```

用一句话说：
- 先保证方案可行（产线对、紧急的先做、别在停机时候排、厚度别乱跳、子任务别倒序）
- 然后尽量满足过滤器后的特殊规则和库存充足的不提前原则
- 最后在这些前提下追求连续生产不打断、换型时间最短、库存紧急的排前面、尽量贴近期望时间、尽量去偏好产线。

---

### 五、输入数据

|数据|来源|说明|
|---|---|---|
|母卷生产任务列表|月度预测确认|型号、配方、厚度、数量、生产时长、库存天数、期望开始时间、允许产线|
|产线信息|基础数据|2条产线的编号、日产能|
|换型时间矩阵|基础数据|配方_型号维度 和 厚度维度 的切换时间表|
|过滤器更换计划|计划员录入|哪条线、什么时候换、停机多久|
|例外时间|计划员录入|产线故障维修等不可用时段|
|工厂日历|基础数据|工作日/休息日、班次、加班安排|
|型号与产线偏好|基础数据|QDJY优先二线、ESYH优先四线等|

---

### 六、输出结果

对每条产线：一个有序的任务列表（经后处理合并），每个任务包含：

- 在产线上的排序位置
- 计划开始时间
- 计划结束时间
- 与前一个任务的换型时间
- 如被拆分：标注该任务块覆盖了原任务的第几天到第几天

整体输出还包括：

- 总换型时间
- 各产线产能利用率
- 库存天数优先级满足情况
- 任务连续性情况（哪些任务被打断、打断原因）

---

### 七、整体排程流程

```
输入：原始母卷任务列表（每个任务可能跨多天）
  ↓
预处理：按天拆分（>1天的拆成多个1天子任务）
  ↓
Solver 排程：所有日颗粒度的任务一起排
  ↓
后处理：合并同产线上连续的同产品子任务，还原为完整任务块
  ↓
输出：每条产线的生产计划
```

#### 7.1 预处理：按天拆分

```java
public List<ProductionTask> splitTasks(List<ProductionTask> originalTasks) {
    List<ProductionTask> result = new ArrayList<>();

    for (ProductionTask original : originalTasks) {
        int totalDays = (int) Math.ceil(original.getProductionHours() / 24.0);

        if (totalDays <= 1) {
            original.setSplit(false);
            result.add(original);
        } else {
            double dailyHours = original.getProductionHours() / totalDays;
            for (int day = 1; day <= totalDays; day++) {
                ProductionTask sub = new ProductionTask();
                sub.setTaskId(original.getTaskId() + "-" + day);
                sub.setParentTaskId(original.getTaskId());
                sub.setDayIndex(day);
                sub.setTotalDays(totalDays);
                sub.setSplit(true);
                sub.setFormula(original.getFormula());
                sub.setModel(original.getModel());
                sub.setThickness(original.getThickness());
                sub.setStockCoverDays(original.getStockCoverDays());
                sub.setAllowedLineIds(original.getAllowedLineIds());
                sub.setPreferredLineId(original.getPreferredLineId());
                sub.setExpectedStart(original.getExpectedStart());
                sub.setProductionHours(dailyHours);
                sub.setQuantity(original.getQuantity() / totalDays);
                result.add(sub);
            }
        }
    }
    return result;
}
```

#### 7.2 后处理：合并

```java
public List<MergedTask> mergeTasks(ProductionLine line) {
    List<MergedTask> result = new ArrayList<>();
    List<ProductionTask> tasks = line.getTaskList();

    int i = 0;
    while (i < tasks.size()) {
        ProductionTask current = tasks.get(i);
        int j = i + 1;
        while (j < tasks.size()
                && tasks.get(j).getParentTaskId() != null
                && tasks.get(j).getParentTaskId().equals(current.getParentTaskId())
                && tasks.get(j).getDayIndex() == current.getDayIndex() + (j - i)) {
            j++;
        }

        MergedTask merged = new MergedTask();
        merged.setOriginalTaskId(
            current.getParentTaskId() != null ? current.getParentTaskId() : current.getTaskId());
        merged.setLineId(line.getLineId());
        merged.setPlannedStart(tasks.get(i).getPlannedStart());
        merged.setPlannedEnd(tasks.get(j - 1).getPlannedEnd());
        merged.setDaysCovered(j - i);
        merged.setTotalDays(current.getTotalDays());
        result.add(merged);

        i = j;
    }
    return result;
}
```

---

### 八、推导量

一旦确定了"每个子任务在哪条产线的第几个位置"，以下值自动推导：

**换型时间**：
- 查换型矩阵，取 max(配方_型号维度, 厚度维度)。
- 产线上第一个任务换型时间为0。
- 同母任务的相邻子任务因属性完全一致，换型时间自动为0。

**计划开始时间**：

- 产线上第一个任务：从排程起始时间开始，跳过非工作时间
- 后续任务：前一个任务的结束时间 + 换型时间，跳过非工作时间（休息日、停机等）

**计划结束时间**：开始时间 + 生产时长，跳过非工作时间。

---

### 九、Timefold 建模方案

#### 9.1 建模方式

采用 `@PlanningListVariable`。每条产线维护一个有序任务列表，Solver 决定每个子任务放在哪条产线的哪个位置。

ProductionLine 是 Planning Entity（拥有列表），ProductionTask 是 Planning Value（被放入列表）。

#### 9.2 Domain Model

**ProductionLine（Planning Entity）**

```java
@PlanningEntity
public class ProductionLine {

    private String lineId;
    private String lineName;
    private double dailyCapacity;

    @PlanningListVariable
    private List<ProductionTask> taskList = new ArrayList<>();
}
```

**ProductionTask（Planning Value / Problem Fact）**

```java
public class ProductionTask {

    // ---- 原有属性 ----
    private String taskId;
    private String materialCode;
    private String formula;
    private String model;
    private int thickness;
    private double quantity;
    private double productionHours;
    private double currentStock;
    private double monthlyShipment;
    private int stockCoverDays;
    private LocalDateTime expectedStart;
    private Set<String> allowedLineIds;
    private String preferredLineId;

    // ---- 拆分相关 ----
    private String parentTaskId;       // 归属的母任务ID，未拆分的为null
    private int dayIndex;              // 第几天（1, 2, 3...）
    private int totalDays;             // 母任务总天数
    private boolean isSplit;           // 是否拆分出来的

    // ---- Shadow Variable ----
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private int changeoverMinutes;
}
```

**其他 Problem Fact**

```java
public class ChangeoverEntry {
    private String fromFormulaModel;
    private String toFormulaModel;
    private int formulaModelMinutes;
    private int fromThickness;
    private int toThickness;
    private int thicknessMinutes;
}

public class FilterChangePlan {
    private String lineId;
    private LocalDateTime changeTime;
    private int downtimeMinutes;
    private LocalDateTime windowEnd;   // changeTime + 20天
}

public class FilterPrioritySequence {
    private Map<String, Integer> modelPriorityMap;
    public int getPriority(String model, int thickness) { ... }
}

public class ExceptionTime {
    private String lineId;
    private LocalDateTime start;
    private LocalDateTime end;
}

public class FactoryCalendar {
    private Set<LocalDate> workingDays;
    private Set<LocalDate> holidays;
    private Map<LocalDate, List<ShiftTime>> overtimeSchedule;
}
```

**ProductionSchedule（Planning Solution）**

```java
@PlanningSolution
public class ProductionSchedule {

    @PlanningEntityCollectionProperty
    private List<ProductionLine> lines;

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<ProductionTask> allTasks;

    @ProblemFactCollectionProperty
    private List<ChangeoverEntry> changeoverMatrix;

    @ProblemFactCollectionProperty
    private List<FilterChangePlan> filterPlans;

    @ProblemFactCollectionProperty
    private List<ExceptionTime> exceptionTimes;

    @ProblemFactProperty
    private FactoryCalendar calendar;

    @ProblemFactProperty
    private FilterPrioritySequence filterPriority;

    @PlanningScore
    private HardMediumSoftScore score;
}
```

#### 9.3 Score 体系

采用 **HardMediumSoftScore** 三级得分。

- Hard：HC1-HC5
- Medium：MC1-MC2
- Soft：SC1-SC5

#### 9.4 约束实现

**ConstraintProvider 框架**

```java
public class SchedulingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            // Hard
            productLineMatch(factory),             // HC1 产品-产线强约束
            urgentStockFirst(factory),             // HC2 库存<10天强制优先
            downtimeConflict(factory),             // HC3 停机时间冲突
            thicknessRotation(factory),            // HC4 厚度轮转约束
            splitTaskOrderSameLine(factory),       // HC5 同产线子任务保序

            // Medium
            filterPriorityOrder(factory),          // MC1 过滤器后20天优先顺序
            noAdvanceForHighStock(factory),        // MC2 库存>30天不前移

            // Soft
            minimizeChangeover(factory),           // SC1 换型时间最小化          权重 1
            stockDaysPriority(factory),            // SC2 库存天数优先级          权重 2
            expectedStartDeviation(factory),       // SC3 期望时间偏差            权重 1
            linePreference(factory),               // SC4 产线偏好                权重 1
            continuousProduction(factory),         // SC5 尽可能连续生产          权重 10/1000
        };
    }
}
```

**约束权重**

```
Hard:
  HC1 产品-产线强约束              1 hard / 违反
  HC2 库存<10天强制优先             1 hard / 违反次数（只看 dayIndex=1 的子任务）
  HC3 停机时间冲突                  1 hard / 重叠分钟数
  HC4 厚度轮转约束                  1 hard / 违反次数
  HC5 同产线内子任务保序             1 hard / 违反次数

Medium:
  MC1 过滤器后20天优先顺序          1 medium / 偏差
  MC2 库存>30天不前移               1 medium / 提前小时数

Soft:
  SC1 换型时间最小化                1 soft / 分钟
  SC2 库存天数优先级                2 soft
  SC3 期望生产时间偏差              1 soft / 小时
  SC4 产线偏好                      1 soft / 违反
  SC5 尽可能连续生产                10 soft（同产线插队）/ 1000 soft（跨产线中断）
```

#### 9.5 关键约束的具体实现

**HC2：库存<10天强制优先（只看第一天子任务）**

```java
Constraint urgentStockFirst(ConstraintFactory factory) {
    return factory.forEach(ProductionLine.class)
        .filter(line -> {
            boolean seenNonUrgent = false;
            for (ProductionTask t : line.getTaskList()) {
                if (!isFirstDay(t)) continue;
                if (t.getStockCoverDays() >= 10) seenNonUrgent = true;
                else if (seenNonUrgent) return true;
            }
            return false;
        })
        .penalize(HardMediumSoftScore.ONE_HARD,
            line -> countUrgencyViolations(line.getTaskList()))
        .asConstraint("HC2-库存<10天强制优先");
}

private boolean isFirstDay(ProductionTask t) {
    return !t.isSplit() || t.getDayIndex() == 1;
}
```

**HC5：同产线内子任务保序**

```java
Constraint splitTaskOrderSameLine(ConstraintFactory factory) {
    return factory.forEachUniquePair(ProductionTask.class,
            Joiners.equal(ProductionTask::getParentTaskId))
        .filter((t1, t2) -> t1.getParentTaskId() != null)
        .filter((t1, t2) -> {
            String line1 = getAssignedLineId(t1);
            String line2 = getAssignedLineId(t2);
            if (!line1.equals(line2)) return false;
            if (t1.getDayIndex() < t2.getDayIndex()) {
                return indexOf(t1) > indexOf(t2);
            } else {
                return indexOf(t2) > indexOf(t1);
            }
        })
        .penalize(HardMediumSoftScore.ONE_HARD)
        .asConstraint("HC5-同产线子任务保序");
}
```

**SC5：尽可能连续生产**

```java
Constraint continuousProduction(ConstraintFactory factory) {
    return factory.forEach(ProductionTask.class)
        .filter(t -> t.isSplit() && t.getDayIndex() > 1)
        .penalize(HardMediumSoftScore.ONE_SOFT,
            t -> {
                ProductionTask prevDay = findPrevDaySibling(t);
                if (prevDay == null) return 0;

                String prevLine = getAssignedLineId(prevDay);
                String currentLine = getAssignedLineId(t);

                if (!prevLine.equals(currentLine)) {
                    return 1000;  // 跨产线中断：开机成本极高
                }

                if (isDirectlyAfter(prevDay, t)) {
                    return 0;    // 同产线且连续：无额外成本
                } else {
                    return 10;   // 同产线但被插队：额外换型成本
                }
            })
        .asConstraint("SC5-尽可能连续生产");
}
```

#### 9.6 Shadow Variable

自定义 VariableListener，当产线的 taskList 变化时自动重算 plannedStart、plannedEnd、changeoverMinutes。

**换型时间计算**：用 HashMap 缓存做 O(1) 查询。

```java
Map<String, Integer> formulaModelCache;  // "A_T19EST->B_T4FDX" → 30
Map<String, Integer> thicknessCache;     // "188->225" → 15

public int calcChangeover(ProductionTask prev, ProductionTask current) {
    if (prev == null) return 0;
    int fmTime = formulaModelCache.getOrDefault(
        prev.getFormula()+"_"+prev.getModel()+"->"+current.getFormula()+"_"+current.getModel(), 0);
    int thTime = thicknessCache.getOrDefault(
        prev.getThickness()+"->"+current.getThickness(), 0);
    return Math.max(fmTime, thTime);
}
```

**时间推导**：

```
第一个任务：plannedStart = 排程起始时间（跳过非工作时间）
后续任务：  plannedStart = prev.plannedEnd + changeoverMinutes（跳过非工作时间）
所有任务：  plannedEnd = plannedStart + productionHours（跳过非工作时间）
```

**VariableListener 带提前终止**：

```java
public void afterListVariableChanged(
        ScoreDirector<ProductionSchedule> scoreDirector,
        ProductionLine line, int fromIndex, int toIndex) {

    List<ProductionTask> tasks = line.getTaskList();
    int startIdx = Math.max(0, fromIndex - 1);

    for (int i = startIdx; i < tasks.size(); i++) {
        ProductionTask current = tasks.get(i);
        ProductionTask prev = (i > 0) ? tasks.get(i - 1) : null;

        int newChangeover = calcChangeover(prev, current);
        LocalDateTime newStart = calcStart(prev, newChangeover, line);
        LocalDateTime newEnd = calcEnd(newStart, current.getProductionHours(), line);

        if (newChangeover == current.getChangeoverMinutes()
                && Objects.equals(newStart, current.getPlannedStart())
                && Objects.equals(newEnd, current.getPlannedEnd())) {
            break;
        }

        scoreDirector.beforeVariableChanged(current, "changeoverMinutes");
        scoreDirector.beforeVariableChanged(current, "plannedStart");
        scoreDirector.beforeVariableChanged(current, "plannedEnd");
        current.setChangeoverMinutes(newChangeover);
        current.setPlannedStart(newStart);
        current.setPlannedEnd(newEnd);
        scoreDirector.afterVariableChanged(current, "changeoverMinutes");
        scoreDirector.afterVariableChanged(current, "plannedStart");
        scoreDirector.afterVariableChanged(current, "plannedEnd");
    }
}
```

#### 9.7 Pinning

```java
public class TaskPinningFilter
        implements PinningFilter<ProductionSchedule, ProductionTask> {
    @Override
    public boolean accept(ProductionSchedule schedule, ProductionTask task) {
        return task.isPinned();
    }
}
```

- 已下达的生产任务单：`pinned = true`
- 冻结天数内的任务：`pinned = true`
- 其余：`pinned = false`

#### 9.8 Solver 配置

```xml
<solver>
  <solutionClass>com.changyang.aps.domain.ProductionSchedule</solutionClass>
  <entityClass>com.changyang.aps.domain.ProductionLine</entityClass>

  <scoreDirectorFactory>
    <constraintProviderClass>
      com.changyang.aps.solver.SchedulingConstraintProvider
    </constraintProviderClass>
  </scoreDirectorFactory>

  <termination>
    <minutesSpentLimit>3</minutesSpentLimit>
  </termination>

  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  </constructionHeuristic>

  <localSearch>
    <localSearchType>LATE_ACCEPTANCE</localSearchType>
  </localSearch>
</solver>
```

---

### 十、一个完整排程例子

**输入**：

```
任务A：T19EST，配方X，厚度188，4天
任务B：T4FDX，配方Y，厚度150，1天，库存8天（紧急）
任务C：T24DJX，配方X，厚度200，2天
```

**预处理拆分后（7个子任务）**：

```
A-1, A-2, A-3, A-4（各1天，T19EST/X/188）
B（1天，T4FDX/Y/150，不拆）
C-1, C-2（各1天，T24DJX/X/200）
```

**Solver 排程结果**：

```
产线1: [A-1] → [B] → [A-2] → [A-3] → [A-4] → [C-1] → [C-2]
```

排程原因：

- B 库存8天 < 10天，HC2（Hard）强制优先 → B 必须尽早排
- A-1 先做1天给B让路 → SC5 惩罚一次同产线插队（10 soft）
- B 做完后 A-2/A-3/A-4 连续 → SC5 无额外惩罚
- C-1/C-2 连续 → SC5 无额外惩罚
- 总换型：A→B 一次 + B→A 一次 + A→C 一次 = 3次

**后处理合并**：

```
产线1: [A 第1天] → [B] → [A 第2-4天] → [C 第1-2天]
```

---

### 十一、已讨论的设计决策及理由

|决策|选择|理由|
|---|---|---|
|建模方式|PlanningListVariable|换型优化的核心是序列顺序，ListVariable 的内置 Move 天然支持列表内交换和跨列表移动|
|谁是 Planning Entity|ProductionLine|ListVariable 模式下，拥有列表的对象是 Entity|
|Score 类型|HardMediumSoftScore|MC1/MC2 比 Soft 重要但不是绝对 Hard，需要 Medium 层|
|Local Search 算法|Late Acceptance|比 Tabu Search 参数更少、在排列型问题中表现稳定，后续可用 Benchmarker 验证|
|换型矩阵查询|HashMap 缓存|最高频操作，必须 O(1)|
|Shadow Variable|自定义 VariableListener 带提前终止|交换相邻位置时通常只需重算 3-5 个任务，避免整条产线全部重算|
|任务拆分方式|预处理按天拆分 + 后处理合并|Solver 无需处理拆分逻辑，只排日颗粒度子任务；合并是确定性后处理|
|子任务跨产线|允许但高惩罚|业务确认不必须同产线，但跨产线开机成本很高，用 SC5 高权重惩罚引导|
|HC2 判断范围|只看 dayIndex=1|只要第一天开始生产，库存就在补充，后续子任务不需要强制最前|

---