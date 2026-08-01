# IMU

## 역할

IMU 서브시스템은 **고주파 관성 데이터**를 제공하여 카메라 프레임 사이의 **자세(orientation) 변화**를 추정하고, OpenCV visual tracking이
불안정할 때 **연속성**을 유지한다. HeatMap 누적 시 pose 보간의 기반이 된다.

## 센서 구성

| 센서                | 용도                                |
|-------------------|-----------------------------------|
| Gyroscope         | 단기 각속도 적분, visual tracking 예측     |
| Accelerometer     | 중력 방향 추정, roll/pitch 보조           |
| Magnetometer (선택) | yaw 절대 참조 (금속·자기 간섭 환경에서는 비활성 권장) |

Android `SensorManager` TYPE_GYROSCOPE, TYPE_ACCELEROMETER, (옵션) TYPE_ROTATION_VECTOR 또는 game
rotation vector.

## 샘플링

- **목표 주파수**: SENSOR_DELAY_GAME 이상 (실측 100–200 Hz).
- Ring buffer: 최소 1–2초 분량, 타임스탬프 monotonic ns.
- 배터리 모드: 스캔 비활성 시 센서 등록 해제.

## 타임스탬프 동기화

카메라 프레임 시각 `t_f`에 대해:

1. 버퍼에서 `t ≤ t_f` 구간의 gyro/accel 샘플을 선택.
2. 프레임 간 `[t_{f-1}, t_f]` 구간을 **각속도 적분**하여 ΔR (rotation delta) 산출.
3. (선택) Complementary filter 또는 rotation vector로 절대 자세 `R_wb` 유지.

Camera와 IMU clock offset은 앱 시작 시 또는 Debug “sync tap”으로
보정 ([Camera.md](./Camera.md), [Debug.md](./Debug.md)).

## 융합 정책 (1차 설계)

초기 버전은 **느슨한 결합**:

- **Visual 정상**: OpenCV homography/flow + IMU ΔR 일치도가 임계값 이상이면 visual translation/homography 우선,
  IMU는 smoothing.
- **Visual degraded**: IMU ΔR만으로 pose propagation; translation은 속도 0 또는 이전 속도 decay.
- **재획득**: visual inlier ratio 회복 시 점진적 blend (hard switch 금지).

고급 VIO(EKF, preintegration)는 로드맵 후반 옵션 ([Roadmap.md](./Roadmap.md)).

## 좌표계

- **World (W)**: CAD에서 정의 ([CAD.md](./CAD.md)).
- **Body (B)**: 단말 기기 고정 좌표 (Android sensor axes).
- **Camera (C)**: optical frame; `T_wb`, `T_bc`(CAD/캘리브)로 연결.

모든 fusion 출력은 `T_wc(t)` 또는 2D 작업 평면상 `(x, y, θ)`로 통일해 HeatMap에 전달.

## 보정

- **Gyro bias**: 정지 구간(사용자 “캘리브레이션”)에서 평균 bias 추정.
- **Extrinsic `T_bc`**: CAD 마운트 nominal + (선택) 소규모 사용자 fine-tune.
- Magnetometer: 기본 off; 켤 경우 soft-iron 보정은 Debug 전용.

## 품질·HeatMap 연동

- IMU-only 구간은 HeatMap 셀 가중치 `w_imu < w_visual`.
- 급격한 jerk / saturation 감지 시 해당 구간 누적 제외 또는 flag.

## 실패 모드

| 상황         | 동작                             |
|------------|--------------------------------|
| 센서 미지원     | Gyro 없으면 visual-only; UI 경고    |
| 샘플 gap     | 보간 실패 시 해당 프레임 fusion skip     |
| 사용자 흔들림 과다 | motion gate; OpenCV 품질과 AND 조건 |

## 관련 문서

- [Architecture.md](./Architecture.md)
- [Camera.md](./Camera.md)
- [OpenCV.md](./OpenCV.md)
- [CAD.md](./CAD.md)
- [HeatMap.md](./HeatMap.md)
