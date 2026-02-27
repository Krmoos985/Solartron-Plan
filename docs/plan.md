# 长阳科技母卷排程系统 - Timefold Solver 实现计划

  

## Context

  

为长阳科技构建基于 Timefold Solver 1.31.0 的母卷（Mother Roll）生产排程系统。

系统基于预测驱动，将母卷生产订单按优先级排列到两条产线上，满足厚度轮转、换型优化、库存优先等排程规则。

使用 `@PlanningListVariable` 建模（对应 VRP 模式：ProductionLine = Vehicle，MotherRollOrder = Visit）。

  

---

## 项目结构

  

```

changyang-scheduling/

├── pom.xml

└── src/main/

    ├── java/com/changyang/scheduling/

    │   ├── ChangyangSchedulingApplication.java

    │   ├── domain/

    │   │   ├── ProductionLine.java        ← PlanningEntity + @PlanningListVariable

    │   │   ├── MotherRollOrder.java       ← PlanningEntity + PlanningValue + 4个影子变量

    │   │   └── MotherRollSchedule.java    ← @PlanningSolution

    │   ├── solver/

    │   │   └── MotherRollConstraintProvider.java  ← 所有约束

    │   ├── service/

    │   │   └── SchedulingService.java

    │   └── rest/

    │       └── SchedulingController.java

    └── resources/

        ├── application.properties

        └── solverConfig.xml

```

  

---

  

## 核心建模架构

  

```

PlanningSolution: MotherRollSchedule

│

├── @PlanningEntityCollectionProperty

│   ProductionLine (LINE_1, LINE_2)

│   └── @PlanningListVariable: List<MotherRollOrder> orders

│

├── @PlanningEntityCollectionProperty + @ValueRangeProvider

│   MotherRollOrder (每个订单必须被分配到且仅一条产线)

│   ├── 问题数据: productCode, formulaCode, thickness, quantity,

│   │           currentInventory, monthlyShipment, expectedStartTime,

│   │           compatibleLines, productionDurationHours

│   ├── @InverseRelationShadowVariable  → assignedLine

│   ├── @IndexShadowVariable            → sequenceIndex

│   ├── @PreviousElementShadowVariable  → previousOrder

│   └── @CascadingUpdateShadowVariable  → startTime, endTime

│       (方法: updateStartAndEndTime())

│

└── @PlanningScore: HardMediumSoftScore

```

  

---

  

## pom.xml 关键依赖

  

```xml

<parent>

    <groupId>org.springframework.boot</groupId>

    <artifactId>spring-boot-starter-parent</artifactId>

    <version>3.2.0</version>

</parent>

  

<properties>

    <java.version>17</java.version>

    <timefold.version>1.31.0</timefold.version>

</properties>

  

<dependencies>

    <dependency>

        <groupId>ai.timefold.solver</groupId>

        <artifactId>timefold-solver-spring-boot-starter</artifactId>

        <version>${timefold.version}</version>

    </dependency>

    <dependency>

        <groupId>org.springframework.boot</groupId>

        <artifactId>spring-boot-starter-web</artifactId>

    </dependency>

    <dependency>

        <groupId>ai.timefold.solver</groupId>

        <artifactId>timefold-solver-test</artifactId>

        <version>${timefold.version}</version>

        <scope>test</scope>

    </dependency>

</dependencies>

```

  

---

  

## 步骤一：ProductionLine.java

  

```java

@PlanningEntity

public class ProductionLine {

    private String id;          // "LINE_1", "LINE_2"

    private String name;        // "一线", "二线"

    private String lineCode;    // 用于软约束匹配："LINE_2" (QDJY优先), "LINE_4" (ESYH优先)

    private LocalDateTime availableFrom;  // 产线可用开始时间

  

    @PlanningListVariable

    private List<MotherRollOrder> orders = new ArrayList<>();

  

    // 无参构造 + 全参构造 + Getters/Setters

}

```

  

---

  

## 步骤二：MotherRollOrder.java

  

```java

@PlanningEntity

public class MotherRollOrder {

    // 问题数据（Problem Facts）

    private String id;

    private String productCode;           // 型号，如 "T10ESY", "QDJY", "T61ESYH"

    private String formulaCode;           // 配方编码

    private double thickness;             // 厚度，用于轮转约束

    private int quantity;

    private double currentInventory;

    private double monthlyShipment;

    private LocalDateTime expectedStartTime;

    private List<String> compatibleLines; // 兼容产线 lineCode 列表

    private double productionDurationHours;

  

    // 影子变量 1：所在产线

    @InverseRelationShadowVariable(sourceVariableName = "orders")

    private ProductionLine assignedLine;

  

    // 影子变量 2：在产线中的位置索引（0-based，未分配时为 null）

    @IndexShadowVariable(sourceVariableName = "orders")

    private Integer sequenceIndex;

  

    // 影子变量 3：前一个生产订单（队列首个为 null）

    @PreviousElementShadowVariable(sourceVariableName = "orders")

    private MotherRollOrder previousOrder;

  

    // 影子变量 4+5：级联计算开始/结束时间（共用同一个方法）

    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")

    private LocalDateTime startTime;

  

    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")

    private LocalDateTime endTime;

  

    // 级联更新回调（Timefold 自动调用，从变化点向后级联传播）

    public void updateStartAndEndTime() {

        if (assignedLine == null) { startTime = null; endTime = null; return; }

        startTime = (previousOrder == null)

            ? assignedLine.getAvailableFrom()

            : previousOrder.getEndTime();

        if (startTime != null) {

            endTime = startTime.plusMinutes((long)(productionDurationHours * 60));

        }

    }

  

    // 业务方法

    public double getInventorySupplyDays() {

        if (monthlyShipment <= 0) return Double.MAX_VALUE;

        return (currentInventory / monthlyShipment) * 30.0;

    }

  

    public boolean isCompatibleWith(ProductionLine line) {

        return compatibleLines != null && compatibleLines.contains(line.getLineCode());

    }

}

```

  

---

  

## 步骤三：MotherRollSchedule.java

  

```java

@PlanningSolution
public class MotherRollSchedule {
 @PlanningEntityCollectionProperty
    private List<ProductionLine> productionLines;

  

    // 双重角色：既是 PlanningEntity（影子变量），也是 @PlanningListVariable 的值范围

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<MotherRollOrder> orders;

  

    @PlanningScore
    private HardMediumSoftScore score;

}

```

  

---

  

## 步骤四：MotherRollConstraintProvider.java

  

### 约束总览

  

| 层级 | 约束名称 | 规则 |
|------|----------|------|
| Hard | productLineMustBeCompatible | 订单必须在兼容产线上 |
| Hard | thicknessMonotonicity | 同一产线内厚度必须单调（不能先升后降或先降后升）|
| Medium | prioritizeByInventorySupplyDays | 库存天数短的订单排序靠前，按天数差惩罚 |
| Soft | respectExpectedStartTime | 实际开始晚于期望开始时间，按分钟数惩罚 |
| Soft | sameFormulaAndProductCodeAdjacent | 相邻订单换型惩罚 -10 |
| Soft | preferredAdjacentPairs | 白名单型号对相邻奖励 +10（净效果：免惩罚）|
| Soft | qdjyPreferLine2 | QDJY型不在LINE_2则惩罚 -5 |
| Soft | esyhPreferLine4 | ESYH型不在LINE_4则惩罚 -5 |

  

### 厚度单调性实现（三元组方向检测）

  

```java

// 通过 Joiners.equal 连接"相邻三元组"(prev→curr→next)

// 检测 prev→curr 方向与 curr→next 方向是否反转

factory.forEach(MotherRollOrder.class)

    .filter(curr -> curr.getPreviousOrder() != null && curr.getAssignedLine() != null)

    .join(MotherRollOrder.class,

          Joiners.equal(curr -> curr, next -> next.getPreviousOrder()))

    .filter((curr, next) -> sameLine(curr, next) && isDirectionReversed(curr, next))

    .penalize(HardMediumSoftScore.ONE_HARD)

    .asConstraint("Thickness must be monotonic within production line");

```

  

### 换型优化白名单型号对

  

```java

private static final Set<String> PREFERRED_ADJACENT_PAIRS = Set.of(

    "T10ESY|T61ESYH",  "T61ESYH|T10ESY",

    "T29DJY|T29DJX",   "T29DJX|T29DJY",

    "T42DJX|T9EST",    "T9EST|T42DJX",

    "T29DJX|T42DJX",   "T42DJX|T29DJX",

    "T29QDJY|T29DJY",  "T29DJY|T29QDJY",

    "T24DJX|T24DJY",   "T24DJY|T24DJX"

);

```

  

---

  

## 步骤五：solverConfig.xml

  

```xml

<solver>

    <solutionClass>com.changyang.scheduling.domain.MotherRollSchedule</solutionClass>

    <!-- 注意：两个 entityClass 都必须声明 -->

    <entityClass>com.changyang.scheduling.domain.ProductionLine</entityClass>

    <entityClass>com.changyang.scheduling.domain.MotherRollOrder</entityClass>

    <scoreDirectorFactory>

        <constraintProviderClass>

            com.changyang.scheduling.solver.MotherRollConstraintProvider

        </constraintProviderClass>

    </scoreDirectorFactory>

    <termination>

        <minutesSpentLimit>5</minutesSpentLimit>

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

  

## 步骤六：SchedulingService.java + SchedulingController.java

  

- `SchedulingService`：注入 `SolverManager<MotherRollSchedule, String>`，提供 `solve()`（同步）和 `solveAsync()`（异步）

- `SchedulingController`：REST 端点 `POST /api/scheduling/solve`，`POST /api/scheduling/solve-async`，`GET /api/scheduling/status/{jobId}`，`DELETE /api/scheduling/stop/{jobId}`

  

---

  

## 待讨论/后续扩展

  

以下需求尚未在本计划中实现，后续可扩展：

  

1. **过滤器更换策略**：换过滤器后20天内强制优先特定型号顺序（T19EST→T4FDX/T4FDY→T5FDX→T7FDX→...），可用带时间窗口的 Hard 约束或额外的 Medium 约束实现

2. **拆产线逻辑**（规则8）：计算每日产出，判断是否需要双线生产及数量拆分，可作为前处理步骤（在 Solver 外部预计算，生成 MotherRollOrder 列表后再传入 Solver）

3. **第二次快速排程**（规则9）：固定产线和顺序，只重算时间，可用 `@PlanningPin` 冻结已有分配，或直接用影子变量重新计算时间


---

  

## 验证方案

  

1. 单元测试：用 `timefold-solver-test` 的 `ConstraintVerifier` 对每个约束写独立测试

2. 集成测试：构造5-10个真实订单，POST 到 `/api/scheduling/solve`，验证返回结果的 score 无 Hard 违反

3. 手工验证：检查返回 JSON 中每条产线的 `orders` 列表，确认厚度单调、产线兼容、库存优先级正确