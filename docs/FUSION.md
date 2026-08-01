# Sensor Fusion

## 목적

STEP 09 — Camera(OpenCV) `DistanceEvent` 와 IMU `ShockEvent` 를 **시간 기반 Rule Engine** 으로 융합하여 `FusionEvent` 를 발행한다.

Fusion은 **Event만** 구독/발행한다. Camera / IMU Feature API를 직접 호출하지 않는다.

관련: [EVENT_SYSTEM.md](./EVENT_SYSTEM.md), [Architecture.md](./Architecture.md), [DECISIONS.md.txt](./DECISIONS.md.txt)

---

## Fusion Pipeline

```
Camera → DistanceEvent ─┐
                        ├─► FusionProcessor (buffer + match)
IMU    → ShockEvent    ─┘
                            ↓
                      FusionRuleEngine
                            ↓
                       FusionResult
                            ↓
                       FusionEvent → EventBus
                            ↓
              STEP10 Map Matching (future)
              STEP11 CAD Viewer (future)
              STEP12 HeatMap (future)
```

Calibration 상태는 `CalibrationEvent` 구독(+ 기동 시 저장된 캘리브 여부)으로만 반영한다.

---

## Timestamp Matching

1. `FusionProcessor` 가 Distance / Shock 를 각각 버퍼에 적재한다.
2. 상대 이벤트가 `|Δt| ≤ FusionConfig.timeWindowNs` 이면 동일 관측으로 매칭한다.
3. `maximumDelayNs` 보다 오래된 버퍼 항목은 폐기한다.
4. 매칭 성공 시 `FusionEventType.FUSED` 로 `FusionResult` 를 생성한다.

---

## Rule Engine

`FusionRuleEngine` 만 Rule Base 로직을 가진다.

- AI / ML / TFLite / YOLO **금지**
- Minimum tracking count / minimum confidence 게이트
- Confidence 가산은 엔진 내부에서만 수행

---

## Confidence 계산

가중 평균 (가중치 합으로 정규화, clamp 0..1):

| 성분 | 소스 | Config weight |
|------|------|----------------|
| Distance Confidence | `DistanceEvent.confidence` | `distanceWeight` |
| Shock Confidence | `ShockEvent.confidence` | `shockWeight` |
| Tracking Score | `trackingFeatureCount / trackingCountNorm` | `trackingWeight` |
| Peak Acceleration Score | `peakAcceleration / peakAccelerationNorm` | `peakAccelerationWeight` |
| Calibration Score | calibrated ? 1 : 0 | `calibrationWeight` |
| RANSAC Confidence | `DistanceEvent.confidence` (동일 필드; 분리 필드 미노출) | `ransacWeight` |

`overall < minimumConfidence` 이면 Result 를 버리고 `rejectedCount` 만 증가한다.

---

## FusionResult

필드: `timestamp`, `distance`, `confidence`, `shockLevel`, `trackingCount`, `peakAcceleration`, `eventType`  
(+ debug: `timestampDelayNs`, component confidences, `calibrated`)

**Position 없음** — STEP 10 Map Matching 에서만 위치 계산.

---

## Package

```
fusion/
  FusionEngine.kt
  FusionProcessor.kt
  FusionRuleEngine.kt
  FusionRepository.kt
  FusionConfig.kt
  FusionResult.kt
  FusionConfidence.kt
  FusionStatistics.kt
```

`FusionEvent` / `FusionEventType` 은 `core/event` 에 둔다 (Core ← Feature 의존 금지).

---

## Future Expansion

- STEP 10: `FusionEvent` → Map Matching → Position
- STEP 11: CAD Viewer
- STEP 12: HeatMap 누적
- 향후 AI FusionEstimator 는 별도 구현체로 교체 (현재 Rule만)
