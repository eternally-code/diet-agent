# Multi-Agent 智能餐食推荐平台

**Multi-Agent 智能餐食推荐平台** 是一个基于 Spring Boot + DeepSeek 的多 Agent 智能饮食推荐系统。平台采用 Orchestrator 编排模式，协调 IntentAgent、ClarifyAgent、RecommendResponseAgent、EvaluationJudgeAgent 四个 LLM Agent，结合 Java 规则引擎实现双层校验（LLM 语义理解 + 规则确定性决策），通过意图识别 → 槽位澄清 → 餐食检索重排 → 推荐生成四步流水线，为用户提供个性化餐食推荐。

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 & 构建 | Java 21, Maven |
| 框架 | Spring Boot 3.3.13, MyBatis 3.0.4 |
| LLM 编排 | [AgentScope](https://github.com/agentscope-ai/agentscope) Spring Boot Starter 1.0.11 |
| LLM 模型 | DeepSeek V4 Pro（主模型）/ DeepSeek V4 Flash（轻量模型），OpenAI 兼容协议 |
| 数据库 | MySQL 8.0, 通过 `mysql-connector-j` 连接 |
| 工具库 | Hutool 5.8.30, Lombok |
| 前端 | 原生 HTML/CSS/JS SPA（hash 路由），无框架依赖 |

## 项目结构

```
src/main/java/com/diet/
├── DietApplication.java              # Spring Boot 启动入口
├── config/
│   └── DietAgentScopeConfig.java     # AgentScope 模型 Bean 配置（DeepSeek API）
├── constants/
│   └── DietConstants.java            # 常量（请求头 X-User-Id）
├── enums/
│   ├── Intent.java                   # 意图枚举（6 种）
│   ├── SessionPhase.java             # 会话阶段（START/CLARIFY/RECOMMEND/PLAN）
│   ├── SourceMode.java               # 数据源（PERSONAL/PUBLIC）
│   ├── ClarifyAction.java            # 澄清动作（ASK/READY）
│   └── RiskLevel.java                # 风险等级（LOW/HIGH）
├── model/                            # 数据模型（Record/POJO）
│   ├── ChatRequest.java / ChatResponse.java
│   ├── SessionState.java / SlotBundle.java
│   ├── MealItem.java / MealRequest.java / MealResponse.java
│   ├── IntentResult.java / ClarifyResult.java
│   ├── RecommendResult.java / RecommendedMealOption.java
│   ├── ResponseResult.java
│   ├── RiskGuardResult.java
│   ├── EvaluationRequest.java / EvaluationReport.java / EvaluationJudgeResult.java
│   ├── FeedbackRequest.java / FeedbackRow.java
│   ├── TraceEvaluationResult.java / RequestTraceRow.java / TraceLabelRequest.java
│   └── ...
├── controller/
│   ├── chat/DietChatController.java          # POST /api/v1/diet/chat
│   ├── session/SessionController.java        # POST /api/v1/diet/sessions
│   ├── meal/MealController.java             # CRUD /api/v1/diet/meals/{personal,public}
│   ├── slot/SlotOptionController.java       # GET /api/v1/diet/slot-options
│   ├── feedback/FeedbackController.java     # POST /api/v1/diet/feedback
│   ├── evaluation/EvaluationController.java # POST /api/v1/diet/evaluations
│   └── trace/AgentTraceController.java      # GET/PUT /api/v1/diet/debug/traces
├── service/
│   ├── orchestrator/DietOrchestratorService.java  # 核心状态机编排
│   ├── intent/
│   │   ├── IntentAgentService.java          # LLM 意图识别
│   │   └── IntentReviseService.java         # 规则层意图矫正
│   ├── clarify/
│   │   ├── ClarifyAgentService.java         # LLM 澄清追问
│   │   └── ClarifyRuleService.java          # 规则层槽位检查
│   ├── slot/
│   │   ├── SlotMergeService.java            # 多轮槽位合并
│   │   └── SlotOptionService.java           # 槽位选项字典
│   ├── meal/
│   │   ├── MealService.java                 # 餐食 CRUD
│   │   ├── MealSearchService.java           # 按槽位检索候选
│   │   └── MealRankService.java             # 二次排序打分
│   ├── recommend/RecommendResponseAgentService.java  # LLM 推荐理由 + 口语回复生成
│   ├── risk/RiskGuardService.java           # 健康风险关键词守卫
│   ├── evaluation/
│   │   ├── EvaluationService.java           # 离线评估编排
│   │   └── EvaluationJudgeService.java      # LLM 评估打分
│   ├── feedback/FeedbackService.java         # 用户反馈收集
│   ├── session/
│   │   ├── SessionService.java              # 会话 & 消息管理
│   │   └── SessionStateService.java         # 会话状态读写
│   └── trace/AgentTraceService.java         # 链路追踪
├── agent/
│   ├── builder/                             # 4 个 Agent 构建器
│   │   ├── IntentAgentBuilder.java
│   │   ├── ClarifyAgentBuilder.java
│   │   ├── RecommendResponseAgentBuilder.java
│   │   └── EvaluationJudgeAgentBuilder.java
│   ├── factory/AgentFactory.java            # Agent 工厂
│   └── loader/PromptLoader.java             # Prompt 文件加载器
├── mapper/                                  # MyBatis Mapper 接口
├── util/
│   ├── JsonService.java / LlmJsonService.java  # JSON 工具
│   └── SlotJsonPicker.java                     # 槽位 JSON 字段提取
└── exception/
    ├── DietException.java                  # 业务异常
    └── DietExceptionHandler.java           # 全局异常处理

src/main/resources/
├── application.yml                         # 应用配置
├── db/diet_db.sql                          # 数据库建表 DDL + 初始数据
├── diet/prompts/                           # LLM Prompt 模板
│   ├── intent.txt                          # 意图识别 prompt
│   ├── clarify.txt                         # 追问生成 prompt
│   ├── recommend-response.txt              # 推荐理由 + 回复生成 prompt
│   └── evaluation-judge.txt                # 离线评估 judge prompt
├── mapper/                                 # MyBatis XML 映射文件
└── static/                                 # 前端 SPA
    ├── index.html
    └── assets/
        ├── css/app.css
        └── js/
            ├── api.js                      # API 请求封装
            └── app.js                      # 前端路由 & 页面渲染

src/test/java/com/diet/service/             # 单元测试 & 集成测试
```

## 核心架构：多 Agent 编排状态机

`DietOrchestratorService` 是系统的中枢，每轮用户对话在 `dietChat()` 中经历以下流水线：

```
用户输入
  │
  ├─ 1. 加载/创建会话状态 (SessionState: phase, slots, lastRecommendations)
  ├─ 2. 开启链路追踪 (AgentTraceService)
  ├─ 3. 获取会话级锁 (ConcurrentHashMap, 同 session 串行)
  ├─ 4. 记录用户消息 → diet_messages
  ├─ 5. PERSONAL 模式空库前置检查
  │
  ├─ 6. 意图识别
  │   ├── IntentAgentService (LLM 调用 → IntentResult)
  │   └── IntentReviseService (规则层结合历史状态修正)
  │
  ├─ 7. 按意图路由分发
  │   ├── MEAL_RECOMMENDATION / CLARIFY_NEEDED → 推荐主链路
  │   ├── MEAL_ADJUST → 换一批（排除已推荐 ID）
  │   ├── MEAL_PLAN → 多餐规划
  │   ├── HEALTH_RISK → 返回保守提示
  │   └── OTHER → 固定引导文案
  │
  └─ 8. 推荐主链路
      ├── mergeSlots (多轮槽位合并)
      ├── ClarifyAgent (槽位不足 → 追问 / 足 → 继续)
      ├── MealSearchService (按 slots 从 MySQL 召回候选)
      ├── MealRankService (对候选二次打分排序, top10)
      ├── RecommendResponseAgentService (LLM 生成推荐理由 + 口语回复)
      ├── RiskGuardService (健康风险关键词扫描)
      └── 持久化状态 + 消息 → 返回 ChatResponse
```

### 四种 LLM Agent

| Agent | Prompt 文件 | 职责 |
|---|---|---|
| IntentAgent | `intent.txt` | 将用户自然语言分类为 6 种意图 + 抽取 7 维槽位 |
| ClarifyAgent | `clarify.txt` | 信息不足时生成一句自然的追问 |
| RecommendResponseAgent | `recommend-response.txt` | 为 top3 候选生成推荐理由 + 80-180 字口语回复 |
| EvaluationJudgeAgent | `evaluation-judge.txt` | 离线评估：对 trace 打分（解释质量 1-5, 自然度 1-5） |

### 规则层双层校验

- **意图层**：`IntentReviseService` 在 LLM 输出后结合历史阶段（上轮追问 → 本轮不应判 OTHER）、lastRecommendations 等做二次矫正
- **槽位层**：`ClarifyRuleService` 在 LLM 进入推荐前规则检查必需槽位
- **安全层**：`RiskGuardService` 在最终回复生成后扫描医疗承诺/极端节食/绝对化健康声明/特殊人群等关键词，命中即用 `conservativeMessage` 替换回复

## 数据库表

| 表名 | 说明 |
|---|---|
| `diet_sessions` | 会话状态（phase, slots JSON, last_recommendations JSON） |
| `diet_messages` | 对话消息（role, content, intent, agent_trace_id） |
| `meal_item` | 餐食数据（name, source_type, 7 维槽位 JSON 字段） |
| `diet_slot_option` | 槽位可选值字典（mealTime/mood/scene/healthGoal/cuisine/taste/convenience） |
| `diet_request_trace` | 链路追踪（trace_json, status, expected_intent/slots 标注字段） |
| `recommend_feedback` | 用户反馈（action, rating, reason） |

## API 端点

### 对话推荐

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/diet/chat` | 核心对话接口。请求头 `X-User-Id`；请求体 `ChatRequest{sessionId, message, sourceMode}` |

### 会话管理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/diet/sessions` | 创建新会话，返回 `CreateSessionResponse{sessionId}` |

### 餐食管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/diet/meals/personal` | 获取个人餐食列表 |
| POST | `/api/v1/diet/meals/personal` | 新增个人餐食 |
| PUT | `/api/v1/diet/meals/personal/{mealId}` | 修改个人餐食 |
| DELETE | `/api/v1/diet/meals/personal/{mealId}` | 删除个人餐食 |
| GET | `/api/v1/diet/meals/public` | 获取公共餐食列表 |

### 槽位选项

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/diet/slot-options` | 获取全部槽位可选值 `Map<slotName, List<value>>` |

### 反馈 & 评估

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/diet/feedback` | 提交用户反馈（action/rating/reason） |
| POST | `/api/v1/diet/evaluations` | 触发离线评估 |

### 调试 & Trace

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/diet/debug/traces/{traceId}` | 按 traceId 查询链路 |
| GET | `/api/v1/diet/debug/sessions/{sessionId}/traces` | 按 sessionId 查询链路列表 |
| GET | `/api/v1/diet/debug/traces` | 按时间范围查询（支持 `onlyUnlabeled` 过滤） |
| PUT | `/api/v1/diet/debug/traces/{traceId}/label` | 标注 trace（expectedIntent/expectedSlots） |

### 响应格式 `ChatResponse`

```json
{
  "sessionId": "uuid",
  "traceId": "trace_xxx",
  "responseType": "ANSWER",
  "speechText": "听起来你今天想吃点清淡的...",
  "displayBlocks": [{"itemId": 1, "name": "番茄鸡蛋面", "reason": "..."}],
  "nextAction": "WAIT_USER",
  "clarifyQuestion": null,
  "missingSlots": []
}
```

`responseType` 为 `ANSWER`（推荐结果）或 `CLARIFY`（追问）。

## 配置 & 环境变量

`application.yml` 中的敏感值通过环境变量注入：

| 变量 | 说明 |
|---|---|
| `DB_PASSWORD` | MySQL root 密码（必填） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（必填） |

其他配置：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `diet.llm.main-model` | `deepseek-v4-pro` | 主模型名（意图识别/追问/推荐生成） |
| `diet.llm.light-model` | `deepseek-v4-flash` | 轻量模型名 |
| `diet.session.max-history-turns` | `10` | 上下文最大对话轮数 |
| `server.port` | `8080` | 服务端口 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/diet_db` | 数据库连接 |

## 启动

1. 确保 MySQL 8.0 运行中，执行 `src/main/resources/db/diet_db.sql` 建库建表
2. 设置环境变量：
   ```powershell
   setx DB_PASSWORD "你的MySQL密码"
   setx DEEPSEEK_API_KEY "你的DeepSeek API Key"
   ```
3. 重启终端/IDE 使环境变量生效
4. 启动应用：
   ```bash
   mvn spring-boot:run
   ```
   或在 VS Code 中运行 `DietApplication` launch configuration
5. 浏览器打开 `http://localhost:8080` 进入前端页面

## 7 维槽位体系

系统用 7 个维度的标签描述每道餐食和用户偏好：

| 槽位 | 字段 | 示例 |
|---|---|---|
| 餐次 | `mealTime` | 早餐、午餐、晚餐、夜宵、加餐、三餐 |
| 心情 | `mood` | 疲惫、开心、焦虑、没胃口、想奖励自己 |
| 场景 | `scene` | 工作、校园、周末、加班、聚餐、运动后 |
| 健康目标 | `healthGoal` | 减脂、清淡、高蛋白、增肌、控碳水、暖胃 |
| 菜系 | `cuisine` | 川菜、粤菜、轻食、火锅、素食、粉面 |
| 口味 | `taste` | 辣、清淡、麻辣、酸甜、蒜香、烟火气 |
| 便捷 | `convenience` | 快速、慢享、一人食、少排队、适合备餐 |

## 6 种用户意图

| 意图 | 说明 |
|---|---|
| `MEAL_RECOMMENDATION` | 正常推荐请求 |
| `CLARIFY_NEEDED` | 有就餐意向但信息不足 |
| `MEAL_ADJUST` | 调整/替换已有推荐 |
| `MEAL_PLAN` | 多餐规划 |
| `HEALTH_RISK` | 涉及健康/医疗风险 |
| `OTHER` | 与饮食无关 |

## 前端页面

前端是单页应用（hash 路由），包含以下页面：

| 路由 | 页面 |
|---|---|
| `#/diet` | 首页 |
| `#/diet/chat` | 聊天推荐（核心交互页） |
| `#/diet/meals/personal` | 个人餐食管理 |
| `#/diet/meals/public` | 公共餐食浏览 |
| `#/admin/traces` | Trace 链路查询 |
| `#/admin/evaluations` | 评估管理 |

## 安全设计

- **Health Guard**：`RiskGuardService` 在每次推荐回复生成后做关键词扫描，拦截医疗诊断、极端节食、绝对化健康承诺、特殊人群（孕妇/糖尿病/儿童）等高风险内容
- **数据隔离**：PERSONAL 模式仅查询当前用户的餐食，PUBLIC 模式查询全量公共数据
- **会话串行**：同 sessionId 的并发请求通过 `ConcurrentHashMap` 锁保证状态一致性
