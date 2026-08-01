# CAD

## 역할

CAD 서브시스템은 **기준 좌표계**, **단말·카메라 마운트 기하**, **작업 공간(평면·ROI)** 을 정의한다. OpenCV·IMU가 추정한 상대 motion을 **의미
있는 공간 좌표**로 변환하고 HeatMap 그리드를 고정한다.

## CAD 자산

- **마운트/지그 모델**: 스마트폰을 고정하는 holder의 nominal geometry (STEP/STL/OBJ 등). 앱 번들 또는 사용자 import(SAF).
- **작업 평면/영역**: 스캔 대상 바닥·벽면을 단순 평면 또는 2D 폴리곤으로 근사.
- **마커/ fiducial (선택)**: 평면 위 기준점; OpenCV extrinsic 보조.

1차 릴리스는 **단일 평면 + 직사각형 ROI**로 단순화 가능 ([Roadmap.md](./Roadmap.md)).

## 좌표계 정의

- **World W**: CAD 모델 원점·축 (예: 작업 평면 한 모서리, Z-up).
- **Mount M**: holder 고정 좌표; 단말 장착 시 `T_wm` nominal.
- **Body B / Camera C**: `T_bc` (camera extrinsic), `T_bm` (단말을 mount에 장착했다고 가정할 때).

사용자 입력:

- holder 종류 선택 (preset)
- (선택) 평면 높이·ROI 크기

## 캘리브레이션

| 항목                        | 소스                            | 갱신          |
|---------------------------|-------------------------------|-------------|
| Camera intrinsics K, dist | Camera factory + chessboard   | Debug calib |
| `T_bc`                    | CAD nominal + fine tune       | 세션 시작       |
| W 평면                      | CAD 또는 3-point touch/AR (후순위) | 세션          |

**Nominal-first**: CAD 치수로 `T_bc`, `T_wm`을 로드하고, 현장에서는 gyro bias·yaw offset만 보정하는 경량 절차를 기본으로 한다.

## OpenCV·IMU와의 연결

- OpenCV homography `H`는 이미지 간; CAD 평면 `π_w`와 intrinsics `K`로 decomposition하여 **평면 상 이동** 추정 (단일 평면
  가정).
- IMU `ΔR`은 **camera 또는 body frame**에서 적용 후 `T_wc` 갱신.
- 변환 체인은 **단일 서비스**(`SpatialTransform`)에서만 수행해 이중 정의 방지 ([Architecture.md](./Architecture.md)).

## HeatMap 그리드

- CAD ROI를 **등간격 2D 그리드**로 tessellate (셀 크기 mm 또는 m 단위 설정).
- 셀 인덱스 `(i, j)` ↔ world `(x, y)` 매핑은 CAD 모듈이 소유 ([HeatMap.md](./HeatMap.md)).

## 버전·호환

- CAD preset JSON: holder id, mesh 경로, `T_bc`, ROI, grid resolution.
- 앱 버전 업 시 schema version 필드로 migration.

## UX (설계)

- 설정: holder preset, ROI 크기, 그리드 해상도.
- (후순위) 3D 프리뷰로 holder·ROI 겹침 확인 — OpenGL/Sceneform 등은 로드맵 Phase 4+.

## 관련 문서

- [Architecture.md](./Architecture.md)
- [Camera.md](./Camera.md)
- [OpenCV.md](./OpenCV.md)
- [IMU.md](./IMU.md)
- [HeatMap.md](./HeatMap.md)
