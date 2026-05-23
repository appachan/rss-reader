# 7. Use Koin for DI

Date: 2026-05-23

## Status

Accepted

## Context

Android（Kotlin）プロジェクトの DI ライブラリを選定する。候補は Hilt、Koin、手動 DI。

## Decision

Koin を採用する。

理由：KMP 移行を前提とした選定（ADR-2）。

- Koin は KMP 対応済み。一方 Hilt は Android 専用で、`:shared:domain` / `:shared:infrastructure` を将来 multiplatform 化する際に持ち込めない
- アノテーションプロセッサ不要でセットアップが軽量

## Consequences

- `:app` モジュールで DI 結線を行い、`:feature:*` モジュールは `:shared:infrastructure` に直接依存しないアーキテクチャが確定
- KMP 移行時も Koin をそのまま利用可能
