# 普通版与销售版打包

同一个 `main` 分支维护所有 WeiPlus 功能。默认 `./gradlew :app:assembleRelease` 构建普通版；也可使用：

```bash
./scripts/package_module.sh normal
./scripts/package_module.sh sales
```

销售版等价于 `./gradlew :app:assembleRelease -PwithMyKey=true`。构建会从相邻的 `../MyKey` 仓库执行 `:authorization-runtime:assembleRelease`，并把生成的 AAR 链接进销售版。仓库放在其他位置时传 `-PmyKeyProjectDir=/绝对路径/MyKey`。授权核心保留在 MyKey 仓库；销售版只包含安装 Hook 的适配类。

销售版使用 MyKey 当前正式地址 `https://mykey.iphonevip.vip` 和 Release 策略。更换正式地址时传 `-PmykeyApiBaseUrl=https://...`。缺少 MyKey 仓库、运行库构建失败或地址不是 HTTPS 都会使销售构建失败。

普通版输出到 `dist/Hchat-<版本>-release-signed.apk`，销售版输出到 `dist/Hchat-<版本>-sales-release-signed.apk`。`dist/` 和 AAR 构建产物均被 Git 忽略。现有 GitHub Actions 只调用默认构建，公开 Release 和 Telegram 流程仍只处理普通版。
