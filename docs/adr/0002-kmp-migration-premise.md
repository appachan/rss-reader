# 2. KMP migration premise

Date: 2026-05-23

## Status

Accepted

## Context

本プロジェクトは現時点では Android 単独だが、将来的に iOS を含む KMP (Kotlin Multiplatform) 化を見据えた設計とするか、Android 専用最適化にとどめるかを決める必要がある。

## Decision

KMP 移行を前提とした技術選定・モジュール構成を採用する。Compose Multiplatform (CMP) は使用せず、UI 層は Android Compose / iOS SwiftUI で別実装、ドメイン層・データ層を共通化する方針とする。

理由：

- 本プロジェクトは技術検証を目的としており、Android 専用最適化よりも将来の選択肢を広く残す方が適切
- ドメイン層・データ層の共通化は CMP の成熟度に依存しないため、現時点でも実用的

## Consequences

- `:shared:domain` は Android 依存ゼロを保つ（POKO、Parcelable 不使用、`kotlin("multiplatform")` 化のみで切り替え可能）
- `:shared:infrastructure` も multiplatform 化を見据えた依存選定
- KMP 移行時のイメージ：
  ```
  :shared:domain         → multiplatform 化
  :shared:infrastructure → multiplatform 化
  :app                   → :app:android + :app:ios
  ```
- iOS 側 ViewModel は `lifecycle-viewmodel`（KMP 対応済み）を共通化する選択肢と、SwiftUI の ObservableObject を使う選択肢の両方が残る
