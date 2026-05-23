# 4. Use Navigation3 for navigation

Date: 2026-05-23

## Status

Accepted

## Context

Jetpack Compose 前提のナビゲーションライブラリを選定する。

## Decision

Navigation3 を採用する。大画面対応（リスト・ディテール 2 ペイン構成）と新世代 Navigation の技術検証を目的とする。

理由：

- `:feature:feed` に一覧と詳細を同居させ、Navigation3 の 2 ペイン対応を同一ルートで扱う設計が前提
- 現時点で実験的（experimental）位置づけのライブラリであり、本プロジェクトの技術検証スコープに含まれる

## Consequences

- Jetpack Compose との組み合わせが前提
- KMP への将来移行時にも利用可能な設計となる（ただし KMP は直近スコープ外）
