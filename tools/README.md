# 开发工具目录

这里仅保留可重复使用的生成和验证脚本。它们不参与 Maven 构建，也不会打包进插件 jar。

## 资源生成

- `generate-pinyin.mjs`：根据物品中文名生成拼音搜索数据。
- `test-pinyin-match.mjs`：验证拼音和首字母搜索结果。
- `import-datapack-lang.mjs`：把数据包语言文件合并到自定义翻译资源。

## 资源同步

- `sync-item-images.mjs`：从 MC Item Gallery（mcitemgallery.com）的版本压缩包同步物品图片到 `webroot/item`。默认下载 26.2 压缩包并更新/补齐所有物品图；`--version=1.21.6` 换版本，`--missing` 只补缺失，`--zip-file=路径` 用本地压缩包，`--dry-run` 只统计。

## 构建与发布

- `make-example-package.mjs`：生成网站自定义示例包。
- `verify-example-package.mjs`：验证示例包内容。
- `verify-jar.mjs`：检查构建产物中的类和资源。
- `verify-official-site.mjs`：检查官网资源和正式页面内容。

## 回归验证

- `verify-removeall.mjs`：验证全部删除商店后的记录行为。
- `verify-restore.mjs`：验证商店记录恢复流程。

## 使用约定

1. 从仓库根目录运行脚本，例如 `node tools/verify-jar.mjs`。
2. 运行前先阅读脚本顶部的输入路径和参数。
3. 一次性排障脚本、数据库副本、日志和中间结果不要提交到仓库。
4. 可复用的构建逻辑优先进入 Maven 配置或正式源码。