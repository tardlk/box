# AI 接手入口

开始修改前阅读 [HANDOFF.md](HANDOFF.md)，再按任务阅读：

- [docs/JAR_GUIDE.md](docs/JAR_GUIDE.md)：构建环境、Spider SPI、新增站点步骤。
- [docs/REFERENCES.md](docs/REFERENCES.md)：优先查宿主源码、站点当前页面和公开 JS；含固定版本链接与定位词。
- [docs/RELEASING.md](docs/RELEASING.md)：准备发布文件、MD5、加速地址、原子提交及回滚。

工作约定：

1. 运行源码在 `src/main/java/`；`src/stubs/` 只编译，不打包。Java 8 源码，JDK 17 + Android SDK 30 + D8 35.0.0，最低运行 API 24。
2. 已有站点 key `ikanbot_fresh`、`ivdy` 保持稳定；共享 JAR，新增站点不能覆盖已有配置。
3. 修改运行源码后执行 `bash build.sh`、`bash test.sh`，再做与修改相关的实网验证。文档改动不需要重新发布 JAR。
4. 编译通过、接口取得地址、HLS 可访问、真机播放是不同验证层级，分别记录。爱电影 HLS 已于 2026-10-29 复测通过，但真机播放仍未验证，不能合并宣称。
5. 不改写已发布的 `jar/box-<md5>.jar` 或历史 `config-日期-主题.json`。更新当前配置和新版本 JAR 必须在同一次提交中。
6. 不提交构建缓存、凭据、Cookie、账号令牌、带有效签名的媒体链接或临时抓包。依赖下载有固定 SHA-256。
7. 发布权由当前用户指令决定；本文不额外要求逐步确认，也不授予新的外部操作权限。
