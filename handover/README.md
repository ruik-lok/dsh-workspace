# DSH 运维与 App 源码备份

> 2026-11-06 整理。**已脱敏**：SSH/宝塔/控制台密码、keystore 口令、网关 key 都已替换为 `***已打码***`，完整凭据见手机上的 `交接文档.md` 原件。

## 目录

| 路径 | 内容 |
|---|---|
| `docs/交接文档.md` | 全套运维文档（服务器、域名、QQ 机器人、代理节点、手机 dsh 公网接入、App） |
| `app/` | 控制面板 App 源码（Java + aapt2 构建，无 Gradle） |
| `ops/` | 手机端脚本：反向隧道 `relay.mjs`、网关 `gw.mjs`、`bg.sh`、SSH 小工具 |
| `ops/service.d/` | Magisk 开机常驻脚本：保活（`95-*`）、root 隧道（`96-*`） |
| `server/server.mjs` | 控制台后端（status-api，端口 8097） |
| `server/nginx/` | nginx 站点配置：`dsh.conf`（dsh 反代 + 手机隧道 + 设备控制台）、`saisqi.xyz.conf` |
| `server/device/index.html` | 手机设备控制台网页（宝塔风格，2 秒刷新） |

## 架构速览

```
公网浏览器 ──> nginx(443) ──/dsh/──> 服务器 18090 ──SSH 反向隧道──> 手机 3099（gw）──> 手机 3080（dsh）
                     └─/dev-xxxxxxxx/──> 设备控制台页面 + /api（反代到同一隧道）
```

App（`xyz.saisqi.panel` v3.5）内：状态页展示本机设备 + 服务器指标；设置页可内嵌打开 dsh、设备控制台、重启 DSH。
