# Roadmap

## 현재 상태

- Android Kotlin 앱 골격: `MainActivity`, Material3 테마, `minSdk 29`.
- Camera / OpenCV / IMU / CAD / HeatMap / Debug **미구현**.
- 설계 문서: `docs/` (본 로드맵과 연동).

## Phase 0 — 기반 (완료·유지)

- Gradle, 패키지 `com.example.cnv`, 기본 UI shell.
- 권한 manifest placeholder, 모듈 패키지 구조만 생성 (구현 없이 디렉터리·README 수준 가능).

## Phase 1 — Camera + Debug HUD

**목표**: 분석 프레임 스트림과 FPS/drop HUD.

- CameraX Preview + ImageAnalysis ([Camera.md](./Camera.md)).
- Monotonic timestamp + ring buffer.
- Debug overlay: FPS, drop count ([Debug.md](./Debug.md)).

**완료 기준**: 실기기에서 15+ fps 분석, lifecycle 안전 unbind.

## Phase 2 — OpenCV 파이프라인

**목표**: 특징 추적 + 품질 메트릭.

- OpenCV SDK 통합 ([OpenCV.md](./OpenCV.md)).
- ORB + matching/RANSAC 또는 LK flow.
- Debug: keypoint overlay, ms/frame.

**완료 기준**: 정지·완만 이동에서 inlier ratio 안정, visual lost 플래그 동작.

## Phase 3 — IMU + 시간 동기

**목표**: 프레임 간 ΔR, visual degraded 시 propagation.

- Gyro/accel 등록, 버퍼, camera sync ([IMU.md](./IMU.md)).
- Loose fusion 정책 v1.
- Debug: IMU Hz, fusion mode 표시.

**완료 기준**: visual 가림 시 IMU-only로 pose 연속성 유지 (translation 단순 모델 허용).

## Phase 4 — CAD 프레임 + HeatMap v1

**목표**: preset holder, 평면 ROI, coverage heatmap.

- CAD preset JSON, `T_bc`, grid ([CAD.md](./CAD.md)).
- Pose → cell 누적, top-down 뷰 ([HeatMap.md](./HeatMap.md)).

**완료 기준**: 짧은 스캔 세션 export/import 후 동일 heatmap 재현.

## Phase 5 — 캘리브레이션·품질

**목표**: 현장 usable 정확도.

- Gyro bias wizard, intrinsics chessboard (Debug).
- HeatMap quality weight tuning.
- Thermal/FPS adaptive downgrade ([Camera.md](./Camera.md)).

## Phase 6 — (선택) 고급

- FOV footprint deposit, VIO-style fusion.
- CAD mesh 3D preview, magnetometer.
- Video export, cloud backup.

## 리스크·의존성

| 리스크                     | 완화                                |
|-------------------------|-----------------------------------|
| 기기별 Camera/OpenCV 성능 편차 | Fast/Balanced 프로파일, 기기 화이트리스트 테스트 |
| IMU–camera time skew    | Phase 3 sync offset, Debug 기록     |
| Homography 평면 가정 위반     | ROI를 평면에 가깝게 UX 가이드, quality gate |

## 문서 유지

기능 구현 시 해당 `docs/*.md`의 “역할·계약” 절을 먼저 갱신하고 코드를 맞춘다. Roadmap phase 완료 시 체크리스트를 본 문서에 반영한다.

## 관련 문서

- [Architecture.md](./Architecture.md)
- [Debug.md](./Debug.md)
