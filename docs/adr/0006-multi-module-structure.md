# 6. Multi-module structure

Date: 2026-05-23

## Status

Accepted

## Context

マルチモジュール構成にあたり、モジュール分割の単位と命名規則を決める必要がある。本プロジェクトは技術検証目的のため、理想構成を追求する。

## Decision

以下の構成を採用する：

```
:app
:feature:feed
:shared:domain
:shared:infrastructure
:shared:ui
```

理由：

- **`:feature:feed` に一覧と詳細を同居させる**：同一ドメイン文脈であり、Navigation3（ADR-4）の大画面対応（リスト・ディテール 2 ペイン）でも同一ルートで扱う
- **`:shared:domain` を Android 依存ゼロに保つ**：後で `kotlin("multiplatform")` に切り替えるだけで KMP 化できる（ADR-2）
- **`shared` という命名**：「複数プラットフォームで共有するもの」という意図を明示するため。`core` は Android プロジェクト固有のイメージが強いので避ける
- **`:shared:infrastructure`**：Repository 実装層。`:shared:data` ではなく `infrastructure` としたのは、Ktor / SQLDelight 等の外部システム接続実装をまとめる層であることを示すため
- **`:shared:ui`**：共通 Compose コンポーネント

## Consequences

- DI 結線は `:app` モジュールで行い、`:feature:*` モジュールは `:shared:infrastructure` に直接依存しない（ADR-7）
- `:shared:domain` には Android SDK 依存（Parcelable / Context 等）を一切持ち込まない
