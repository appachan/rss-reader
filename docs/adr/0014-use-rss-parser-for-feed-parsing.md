# 14. Use prof18/RSS-Parser for RSS/Atom parsing

Date: 2026-05-24

## Status

Accepted

## Context

RSS / Atom フィードをパースする必要がある。

## Decision

RSS/Atom パーサに prof18/RSS-Parser（`com.prof18.rssparser:rssparser`）を採用する。

理由（ADR-0002 の KMP 移行前提に沿う）：

- Kotlin Multiplatform 対応で、RSS 2.0 / Atom / RDF / JSON Feed を解釈できる
- JVM 専用の ROME や Android 専用ライブラリは KMP 化できないため候補外

取得は Ktor（ADR-0008）で行い、得た生文字列を `RssParser.parse(...)` に渡す。

## Consequences

- `:shared:infrastructure` に配置する
