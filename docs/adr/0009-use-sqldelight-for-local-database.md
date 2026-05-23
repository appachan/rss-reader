# 9. Use SQLDelight for local database

Date: 2026-05-23

## Status

Accepted

## Context

ローカルデータベースライブラリを選定する。候補は Room、SQLDelight。

## Decision

SQLDelight を採用する。

理由：KMP 移行を前提とした選定（ADR-2）。

- SQLDelight は KMP 文脈で標準的に採用される SQL ライブラリ
- Room は Android 専用、`room-multiplatform` は発展途上のため候補から除外

## Consequences

- KMP 移行時も SQLDelight をそのまま利用可能
- `:shared:infrastructure` モジュールに配置
