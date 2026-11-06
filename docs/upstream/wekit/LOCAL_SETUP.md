# 本地 Codex 一次性准备

目标：本地 Hchat 的指导文件与讨论窗口一致，且有一份准确可检索的 WeKit 参考源码。此任务只准备资料和参考工作区，不构建或安装 APK。

## 1. 同步 Hchat

在实际 WeiPlus 仓库根目录先读取 `AGENTS.md`、`skills/hchat-project/SKILL.md` 和本目录文档，检查：

```sh
git status --short
git branch --show-current
git remote get-url origin
git rev-parse HEAD
```

确认 origin 为 `YYKX682436/WeiPlus`、工作分支符合本仓库只维护 `main` 的约定。保留所有已有未提交内容，不使用 `reset --hard`、`clean -fd`、强制 checkout 或自动丢弃本地改动。

若需获取新文档，先 `git fetch origin main` 并检查 `HEAD...origin/main`。能安全快进时 `git merge --ff-only origin/main`。有本地提交或同路径未提交改动时，先比较具体差异，安全合并能确定的文档变化；确实有冲突才说明冲突文件。不要为准备参考资料变更业务实现、签名、构建配置或其他分支。

## 2. 准备参考源码

默认路径为 `references/WeKit/`，已由根 `.gitignore` 精确忽略。该目录不属于 Gradle 项目，不添加到 `settings.gradle.kts`、依赖或 sourceSets，也不添加 git submodule。

先从 `baseline.json` 读取默认参考 SHA；它是本地默认副本的版本依据。只有在目标目录不存在时，再执行 clone：

```sh
wekit_reference_commit="$(python3 -c 'import json,re; v=json.load(open("docs/upstream/wekit/baseline.json"))["source"]["commit"]; assert re.fullmatch("[0-9a-f]{40}",v); print(v)')"
git clone --no-checkout https://github.com/Ujhhgtg/WeKit.git references/WeKit
git -C references/WeKit checkout --detach "$wekit_reference_commit"
```

若目录已经存在，先核对 origin、HEAD 和 `git status --porcelain`。正确来源且干净的参考仓库，可先 fetch 缺少的固定提交，再安全切到指定 SHA。目录包含用户改动、来源不符或不是 Git 仓库时，保留原样并在 **Hchat 仓库之外**另建不冲突的参考目录，记录实际路径；不要覆盖、重置或删除。不要创建未被忽略的 `references/WeKit-2/` 并让它混入 Hchat 的未跟踪文件。

本阶段读取主仓库源码即可，**不需要构建 WeKit，也不需要一开始递归拉取全部子模块**。具体功能实际依赖 `reflekt`、脚本引擎等子模块时，再按主仓库记录的 gitlink SHA 获取所需源码，并核对那个子模块的许可。需要执行上游自身构建时，另按上游说明使用 `./x` 及所需依赖；直接运行上游 Gradle 不保证 Rust 原生库已重编译。

## 3. 完成核对

```sh
wekit_reference_path="references/WeKit"
git -C "$wekit_reference_path" remote get-url origin
git -C "$wekit_reference_path" rev-parse HEAD
git -C "$wekit_reference_path" status --porcelain
```

若用了仓库外备用副本，把 `wekit_reference_path` 换成实际路径，以上三项必须检查实际副本。默认副本还需检查：

```sh
git check-ignore references/WeKit/README.md
git ls-files references/WeKit
git diff --check
git status --short
```

核对标准：

- origin 指向 `Ujhhgtg/WeKit`；HEAD 与 `baseline.json` 的 `source.commit` 完全一致。
- 参考仓库没有本次引入的源码修改；如保留了已有改动，说明它们并使用另一份干净副本进行分析。
- 默认副本的 `git check-ignore` 命中指定参考路径，`git ls-files references/WeKit` 无输出。备用副本应确认位于 Hchat 仓库之外，并检查 Hchat 最终状态，报告“仓库外参考副本，未进入 Hchat 跟踪”；不要用旧的默认目录检查结果冒充备用副本状态。
- 本目录文档可读；从 `FEATURE_INDEX.md` 任选一条，能打开其源码路径和 JSON 记录。不要把文件可读写成该功能已适配。
- Hchat 最终差异只有必要的文档同步或本地准备记录；不启动 Release workflow、Telegram 分发、真机安装或微信数据操作。

如果网络使 WeKit 拉取失败，可先完成 Hchat 文档核对，明确标记 `REFERENCE_SOURCE_NOT_READY` 并报告实际错误。不要以空目录、伪造源码或“索引已下载”冒充准备成功。

## 4. 回报格式

```text
Hchat：实际目录、分支、HEAD、保留的未提交内容概况
资料：docs/upstream/wekit/README.md 可读 / 不可读
WeKit：实际参考目录、origin、HEAD、工作区状态
隔离：参考源码已忽略且未跟踪 / 具体问题
抽查：中文功能名 → technicalId → 源文件，文件可读
状态：READY / REFERENCE_SOURCE_NOT_READY / 具体阻碍
业务代码与 APK：本次仅准备参考资料
```

不需要提交参考仓库，也不需要为了这一步单独增加版本号、运行 Android 回归或编译 APK。
