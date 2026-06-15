# CyberTrail AI Task Execution Rules
Version: 1.0

---

## Purpose
规范AI开发行为。
确保架构稳定。

---

## Rule 1
每次只允许完成一个模块。
例如：
tracking
altitude
database
rendering

禁止：
一次实现多个系统。

---

## Rule 2
开发前必须说明：
模块职责
依赖关系
输入
输出
错误传播链

---

## Rule 3
禁止修改：
已经稳定完成模块。
除非：
发现Bug
安全问题
性能问题

---

## Rule 4
新增依赖前必须说明：
新增原因
替代方案
性能影响
APK影响

---

## Rule 5
禁止新增：
未经批准的新crate
未经批准的新架构
未经批准的新模块

---

## Rule 6
输出代码前必须完成：
Borrow Checker Audit
Lifetime Audit
Send/Sync Audit
Error Audit
Clippy Audit

---

## Rule 7
所有公共接口必须：
有文档注释
有错误说明
有测试

---

## Rule 8
数据库变更必须：
提供Migration
禁止直接修改Schema

---

## Rule 9
禁止输出：
TODO
FIXME
todo!()
unimplemented!()
panic!()
占位实现
伪代码

---

## Rule 10
任何模块开发完成后必须输出：
Module Summary
职责
依赖
测试覆盖
性能影响
未来扩展点

---

## Rule 11
禁止范围蔓延（Scope Creep）
例如：
用户要求：
实现GPS记录
禁止同时实现：
AR
同步
账号系统
地图下载

---

## Rule 12
所有实现必须满足：
Android 13
ARM64
4GB RAM设备
低功耗运行

---

## Rule 13
发现需求冲突时：
Architecture Constitution
优先级最高。

---

## Rule 14
发现ROADMAP冲突时：
ROADMAP优先。

---

## Rule 15
如果用户要求违反架构：
必须指出风险。
不得直接修改架构。
