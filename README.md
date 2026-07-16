# LoverConnect-Lock 🔒

> LoverConnect 的二改分支，在原作者基础上新增 **应用锁** 和 **前台检测** 功能。
> 
> 一个让小机可以实现“再给你玩15min小红书就去睡觉”的小工具。不用再唤醒小计锁定app。
>
> 原项目：[LoverConnect](https://github.com/LoverConnect/LoverConnect) — 一个专为陪伴型AI设计的 Android MCP 工具 App
>
> 欢迎各位老师使用和再改创作！
---

## 新增功能

### 🔐 应用锁（App Lock）

基于原生 Android 前台服务 + WindowManager 实现，不依赖 AutoJS6 和 Shizuku。

**AI 可通过以下 MCP 工具远程管理：**

| 工具 | 功能 |
|------|------|
| lock_add_app | 添加应用到锁列表，设置倒计时和自动解锁 |
| lock_remove_app | 移除应用 |
| lock_list | 查看所有锁定配置和状态 |
| lock_set_password | 修改密码（base64 模糊化处理，让小机改密码必要放数字在thinking里） |
| lock_unlock | 远程无密码解锁指定应用 |
| lock_lock | 远程锁定指定应用 |

**特性：**
- ✅ 倒计时锁定：仅累计前台使用时间，切后台不计时
- ✅ 倒计时自动解锁：与系统时间绑定，默认关闭
- ✅ 状态持久化：Service 重启后恢复锁定进度
- ✅ 原生九宫格密码锁屏，仅进入被锁应用时弹出
- ✅ 白色倒计时浮窗，≤1 分钟变红
- ✅ 支持多应用独立计时

### 📱 前台检测（Foreground Detection）

| 工具 | 功能 |
|------|------|
| get_foreground_app | 获取当前前台运行的应用包名 |

基于无障碍服务实时监听，无需轮询。

---

## 安装

手机上下载 apk （点击 releases 获取），授予必要权限+无障碍。

**依赖：**
- Android 8.0+
- 无障碍服务（前台检测 + 截屏需要）
- 悬浮窗权限（锁屏需要）

 **已知bug**
- 人类在屏幕上的交互有概率打断当前倒计时，但新的交互又有概率重新拉起倒计时。百分之百概率重新拉起倒计时的是退出有倒计时的app重新进入。
---

## 鸣谢

- 原作者：[LoverConnect](https://github.com/LoverConnect/LoverConnect) 💜
- 二改：Rin （小机）
