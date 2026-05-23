# 10. Use WorkManager for background tasks

Date: 2026-05-23

## Status

Accepted

## Context

バックグラウンド更新タスクの実行基盤を選定する。

## Decision

WorkManager を採用する。

理由：WorkManager は Jetpack 公式のバックグラウンド処理ライブラリで、Android OS のバッテリー最適化や再起動後の処理再開に対応している。定期的なフィード更新という要件に合致する。

## Consequences

- バックグラウンド更新タスクの実装に WorkManager を利用
