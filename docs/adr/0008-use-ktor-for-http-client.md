# 8. Use Ktor for HTTP client

Date: 2026-05-23

## Status

Accepted

## Context

HTTP クライアントライブラリを選定する。候補は Retrofit + OkHttp、Ktor。

## Decision

Ktor を採用する。

理由：KMP 移行を前提とした選定（ADR-2）。

- Ktor Client は KMP ネイティブ対応の HTTP クライアント
- 一方 Retrofit は JVM/Android 専用で、`:shared:infrastructure` を将来 multiplatform 化する際に利用できない

## Consequences

- KMP 移行時も Ktor をそのまま利用可能
- `:shared:infrastructure` モジュールに配置
