# HeatMap

## 역할

HeatMap은 CAD 기준 작업 평면 위에 **공간적으로 누적된 관측·커버리지·품질**을 2D 컬러맵으로 표현한다. 사용자가 “어디를 얼마나, 얼마나 신뢰도 있게 스캔했는지”를
한눈에 파악하는 CNV의 핵심 출력이다.

## 그리드 모델

- **정의**: [CAD.md](./CAD.md) ROI를 `N_x × N_y` 셀로 분할.
- **셀 상태** (최소):
    - `visit_count` — pose 궤적이 셀을 통과·체류한 횟수
    - `weight_sum` — visual/IMU 품질 가중치 누적
    - (선택) `value_max` — 특정 스칼라 관측(예: feature density)의 max

초기 버전은 **coverage heatmap** (`visit_count` + quality weight)에 집중한다.

## 누적 규칙

프레임 주기 또는 pose 업데이트마다:

1. 현재 pose `T_wc`에서 **카메라 위치 (또는 ray-ground intersection)** 를 평면 `(x, y)`로 투영.
2. 해당 `(x, y)`가 속한 셀 `(i, j)` 계산.
3. 품질 가중치 `w = f(visual_inliers, imu_only_flag, motion_blur)` ∈ [0, 1].
4. `visit_count[i,j] += 1`, `weight_sum[i,j] += w`.

**Footprint 확장 (옵션)**: FOV cone을 평면에 투영해 여러 셀에 fractional deposit — Phase 3.

## 정규화·컬러맵

- 표시값: `display = weight_sum / max(visit_count, 1)` 또는 단순 `visit_count` log scale.
- 컬러맵: viridis/plasma 등 perceptually uniform; 저품질 구간은 alpha 또는 hatch overlay.
- 범례: min/max, IMU-only 구간 비율.

## 렌더링

- **오버레이**: 카메라 프리뷰 위 반투명 HeatMap (homography로 ROI warp — 정합 오차 주의).
- **독립 뷰**: CAD ROI top-down Bitmap/Canvas; pinch zoom.
- 갱신 rate: 10–15 Hz로 UI 스로틀 ([Architecture.md](./Architecture.md)).

## 세션·export

- 세션 종료 시 float/int 버퍼 + grid meta (origin, cell size, ROI) JSON export.
- 재로드 시 동일 colormap으로 offline viewing.

## 품질 시각화

- IMU-only 구간: 별도 채널 또는 desaturate.
- visual lost 연속 구간: 경계선 표시 (Debug 연동).

## 파라미터

| 파라미터      | 의미                     | 기본     |
|-----------|------------------------|--------|
| cell_size | CAD 단위 mm              | preset |
| decay     | 시간 decay (미스캔 영역 fade) | off    |
| min_w     | 누적 최소 가중치              | 0.2    |

## 관련 문서

- [Architecture.md](./Architecture.md)
- [CAD.md](./CAD.md)
- [OpenCV.md](./OpenCV.md)
- [IMU.md](./IMU.md)
- [Debug.md](./Debug.md)
