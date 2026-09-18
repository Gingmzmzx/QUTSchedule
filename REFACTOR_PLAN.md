# 重构工作稿（对照 DESIGN.md）

按 DESIGN.md 全量铺开，分批交付，每批结束都要能 `./gradlew :app:assembleDebug :app:testDebugUnitTest` 通过。

## 存储 schema v2

`filesDir/schedule.json`：

```json
{
  "version": 2,
  "semesters": [
    {"id":"...","name":"2026-2027学年第1学期","startDate":"2026-09-07","totalWeeks":20,"schemeId":null}
  ],
  "currentSemesterId": "...",
  "schemes": [
    {"id":"default","name":"默认","builtin":true,"fromMonthDay":null,"toMonthDay":null,
     "slots":[{"start":"08:00","end":"09:40"}]}
  ],
  "currentSchemeId": "default",
  "courses": [
    {"id":"...","semesterId":"...","name":"高等数学D上","teacher":"刘玉香","location":"B104",
     "campus":"黄岛校区","teachingClass":"...","dayOfWeek":1,"startSlot":1,"endSlot":2,
     "weekSpec":"4-17周","weeks":[4,5],"type":"COURSE","date":null,"startTime":null,
     "endTime":null,"note":"理论","custom":false,"reminderEnabled":true,
     "reminderLeadMinutes":-1,"credit":5.0,"assessment":"考试","lab":false}
  ],
  "todos": [
    {"id":"...","title":"交实验报告","type":"HOMEWORK","date":"2026-09-18",
     "startTime":null,"endTime":null,"done":false,"note":""}
  ],
  "profile": {"nickname":"","signature":"","school":"青岛理工大学","college":"","major":"","grade":""},
  "prefs": {
    "theme":"SCROLL","themeMode":"SYSTEM","dynamicColor":false,
    "customColor":null,"backgroundUri":null,"gridHeightScale":1.0,"gridCorner":8,
    "gridGap":2,"gridAlpha":100,"animationScale":1.0,"reducedMotion":false,
    "showWeekend":true,"showNonCurrentWeek":true,"weekStartDay":1,
    "reminderEnabled":true,"defaultReminderMinutes":15,"liveUpdateEnabled":true,
    "dndEnabled":false,"dndMode":"SILENT","holidaySkip":true
  }
}
```

日期一律 `yyyy-MM-dd`，时间一律 `HH:mm`，作息生效区间为 `MM-dd`。

### v1 → v2 迁移

旧文件只有 `{term:{termName,startDate,totalWeeks,defaultReminderMinutes,reminderEnabled,liveUpdateEnabled,slotStarts[5],slotEnds[5]}, courses:[...]}`：

1. `slotStarts/slotEnds` 合成 `schemes[0]`（id `default`）。
2. `term` 合成一个 `Semester`（`name` 取 `termName`），设为当前学期，`schemeId=default`。
3. `courses` 原样搬入，补 `semesterId` 与新增字段默认值。
4. 其余字段取 `prefs` 默认值。
5. 迁移后立即按 v2 回写。

## 批次

| 批次 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 数据层 v2：Semester / TimeScheme / Todo / Profile / Prefs / AppData / Store / Repository | 进行中 |
| 2 | 四页骨架：今日 / 课表 / 日程 / 我的 | 待做 |
| 3 | 二级页：我的信息、导入导出、学期设置、自定义时间段、管理课表、课程管理、提醒设置、外观、备份 | 待做 |
| 4 | 桌面小组件 + 每日零点更新 | 待做 |
| 5 | 提醒增强：免打扰 / 节假日 / 灵动岛时间窗口 | 待做 |
| 6 | 备份同步：JSON / ICS / 系统日历 / WebDAV | 待做 |
| 7 | 外观：三套主题 / 深色 / 动态取色 / 排版优化 | 待做 |

## 关键约定

- 课程 `startSlot/endSlot` 仍是「小节号」1..2N；作息方案的每个 `Slot` 是覆盖两小节的大节，`bandOf(slot)=(slot-1)/2` 直接映射，导入与旧数据都不用改。
- 一门课归属一个学期：`Course.semesterId` 为空时视为当前学期。
- 待办 `Todo` 是单日的，不带周次；日程页把课程与待办混排，`done` 状态两页共享。
