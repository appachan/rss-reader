# 3. Use Jetpack Compose for UI

Date: 2026-05-23

## Status

Accepted

## Context

RSS リーダー Android アプリの UI フレームワークを選定する。

## Decision

Jetpack Compose を採用する。本プロジェクトでは Jetpack Compose + Material3 を UI スタックの前提とする。

理由：

- Android 公式のモダン UI フレームワークであり、XML Views に比べ宣言的に記述できる
- Navigation3（ADR-4）、ViewModel + UiState パターン、Compose プレビューベースのテスト戦略といった後続選定の土台となる
- 本プロジェクトはこの選定を所与とし、選定の比較検討は行わない

## Consequences

- ViewModel + UiState パターンでの状態管理を前提とするアーキテクチャが確定
- Navigation3 との組み合わせが前提
