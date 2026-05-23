# 5. Use simplified Clean Architecture

Date: 2026-05-23

## Status

Accepted

## Context

レイヤー構成と責務分離の方針を決める必要がある。バックエンドは持たない（クライアント単独）が、将来のサーバー移行余地は残したい。

## Decision

Clean Architecture の簡略版を採用する。厳密な依存ルールや過剰な抽象化は避け、UI / Domain / Data の 3 層構造とする。

理由：

- ドメイン層を独立させることで KMP 共通化の対象を明確にできる（ADR-2）
- Data 層を抽象化しておくことで、将来サーバー移行が必要になった際にも UI / Domain を変えずに済む
- ViewModel + UiState パターンとの相性が良い

## Consequences

- レイヤー構成：
  ```
  UI Layer       → Compose + ViewModel（lifecycle-viewmodel KMP 対応済み）
  Domain Layer   → UseCase + Repository Interface（純 Kotlin）
  Data Layer     → Repository 実装 + Ktor + SQLDelight
  ```
- 状態管理は `UiState` data class を `StateFlow` で公開する形に統一
