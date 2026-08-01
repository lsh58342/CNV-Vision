# Debug

## 역할

Debug 계층은 개발·현장 튜닝 시 **파이프라인 내부 상태를 관측**하고, Camera·OpenCV·IMU·CAD·HeatMap 설정을 **안전하게 조정**할 수 있게 한다.
릴리스 빌드에서는 compile-time 또는 runtime flag로 비활성화 가능.

## 빌드·플래그

- `BuildConfig.DEBUG` 또는 `cnv.debug.enabled` — debug 패널·오버레이 게이트.
- ProGuard/R8: debug-only 클래스 keep 규칙 분리.

## UI 구성 (설계)

| 영역      | 내용                                                       |
|---------|----------------------------------------------------------|
| Overlay | 특징점, optical flow, ROI, grid outline                     |
| HUD     | FPS, frame drop, fusion mode (visual/imu/blend), thermal |
| Panel   | Sliders: ORB count, RANSAC thresh, HeatMap cell size     |
| Log     | ring buffer text export                                  |

## 계측 (Metrics)

- Camera: 분석 FPS, drop count, exposure time (가능 시).
- OpenCV: ms/frame, keypoints, inlier ratio.
- IMU: sample Hz, gap count, bias estimate.
- Fusion: visual lost streak, IMU-only ratio.
- HeatMap: cells touched, export size.

## 기록·덤프

- **Snapshot**: 단일 프레임 + Mat overlay PNG.
- **Session trace**: CSV (timestamp, pose, metrics) — PII 없음.
- **Raw frame dump (옵션)**: 저장공간·발열 경고 후 N프레임만.

## 캘리브레이션 도구

- Gyro bias: “기기를 가만히” wizard.
- Camera–IMU time offset: flash or manual tap (후순위).
- Chessboard intrinsics: Debug 전용 Activity ([Camera.md](./Camera.md)).

## 오버레이 좌표

- Keypoint는 **분석 해상도** 기준; Preview에 scale transform 적용.
- HeatMap grid는 CAD top-down 뷰와 프리뷰 overlay 두 경로 일치 검증용 crosshair.

## 성능 프로파일링

- Android Studio Profiler 연동 가이드 (메모리: Mat release).
- Systrace tag: `CNV.Camera`, `CNV.OpenCV`, `CNV.Fusion`.

## 오류 표면

- 사용자-facing toast는 최소화; Debug 패널에 stack trace + error code (CAM_*, CV_*, IMU_*, CAD_*).

## 관련 문서

- [Architecture.md](./Architecture.md)
- [Camera.md](./Camera.md)
- [OpenCV.md](./OpenCV.md)
- [IMU.md](./IMU.md)
- [HeatMap.md](./HeatMap.md)
- [Roadmap.md](./Roadmap.md)
