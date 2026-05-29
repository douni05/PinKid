# 📍 PinKid

> **우리 아이가 있는 곳, 언제나 함께**
>
> 자녀의 실시간 위치를 확인하고, 안전 구역을 벗어나면 즉시 알림을 받는 Android 보호자-자녀 위치 공유 앱

<br>

## 📋 목차

- [앱 소개](#앱-소개)
- [주요 기능](#주요-기능)
- [화면 구성 및 흐름](#화면-구성-및-흐름)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [Firebase 데이터베이스 구조](#firebase-데이터베이스-구조)
- [프로젝트 파일 구조](#프로젝트-파일-구조)
- [시작하기](#시작하기)
- [필요 권한](#필요-권한)
- [팀원](#팀원)

<br>

## 앱 소개

**PinKid**는 보호자와 자녀를 연결하는 Android 위치 공유 앱입니다.  
보호자는 자녀의 실시간 GPS 위치를 지도에서 확인하고, 집·학교 등 안전 구역을 사전에 등록해 두면 자녀가 해당 구역을 벗어났을 때 즉시 푸시 알림을 받을 수 있습니다.

| 구분 | 설명 |
|------|------|
| 플랫폼 | Android (Java) |
| 최소 SDK | API 35 (Android 15) |
| 타겟 SDK | API 36 (Android 16) |
| 백엔드 | Firebase (Auth + Realtime Database) |

<br>

## 주요 기능

### 👨‍👩‍👧 계정 & 연결
- **이메일/비밀번호 회원가입** — 가입 시 보호자(학부모) / 자녀 역할 선택
- **아이 고유 코드** — 자녀 가입 시 6자리 코드 자동 발급, 보호자가 코드를 입력해 1:N 연결
- **연결 관리** — 보호자가 연결 목록에서 직접 아이 추가 및 연결 해제 가능

### 📡 실시간 위치 추적
- **Foreground Service** 기반의 백그라운드 GPS 업데이트 (10초 간격)
- `FusedLocationProviderClient`로 배터리 효율적인 고정밀 위치 수집
- Firebase Realtime Database에 위도·경도·타임스탬프 실시간 동기화
- 보호자 홈 화면 미니 맵 + 전체화면 지도에서 자녀 위치 마커 표시

### 🗺️ 안전 구역 관리 (지오펜스)
- 보호자가 주소와 별명(예: 집, 학교)을 등록하면 자동 지오코딩으로 위도·경도 저장
- **Haversine 공식**으로 자녀↔등록 위치 간 거리 계산 (기준 반경: **300m**)
- 자녀가 구역을 **벗어날 때 / 도착할 때** 모두 보호자에게 푸시 알림 발송
- 복수 구역 등록 가능, 구역별 독립적 상태 추적

### 🚨 SOS 긴급 연락
- 자녀 홈 화면의 **SOS 버튼** 한 번으로 보호자에게 즉시 긴급 알림 발송
- 확인 다이얼로그로 오발송 방지
- 별도 SOS 알림 채널 (최우선 진동·소리)

### ⏱️ 위치 미수신 알림
- 마지막 위치 수신 후 **5분** 이상 업데이트 없으면 보호자에게 알림
- 기기 배터리 방전·네트워크 단절 등 이상 상황을 빠르게 감지

### ⚙️ 설정
- 보호자: 연결된 자녀 목록·본인 정보 확인, 로그아웃, 회원 탈퇴
- 자녀: 연결된 보호자 확인, **내 연결 코드 확인 및 복사**, 로그아웃, 회원 탈퇴
- 탈퇴 시 양방향 연결 데이터(children / linkedWith) 자동 정리

<br>

## 화면 구성 및 흐름

```
[앱 실행]
    └── SplashActivity
          ├── 미로그인 → LoginActivity ──── RegisterActivity (회원가입)
          └── 로그인됨
                ├── role = parent → ParentHomeActivity
                └── role = child
                      ├── linkedWith 있음 → ChildActivity
                      └── linkedWith 없음 → LinkActivity

[LinkActivity]
    ├── 보호자: 아이 코드 입력 → 연결 / 기존 아이 목록 + 연결 해제 → 홈으로
    └── 자녀: 내 코드 표시 → 부모 연결 감지 → 홈으로 가기 버튼 활성화

[ParentHomeActivity]
    ├── 미니 맵에 자녀 위치 실시간 표시
    ├── 현재 주소 역지오코딩 표시
    ├── 지도 클릭 → ParentMapActivity (전체화면)
    ├── 위치 등록하기 → LocationRegisterActivity
    ├── 아이 연결하기 → LinkActivity
    └── 설정(⚙) → SettingsActivity
          ├── 로그아웃 → LoginActivity
          └── 탈퇴하기 → LoginActivity

[ChildActivity]
    ├── 미니 맵에 본인 위치 표시
    ├── Foreground GPS 서비스 자동 시작
    └── 설정(⚙) → ChildSettingsActivity
          ├── 부모 재등록 → LinkActivity
          ├── 로그아웃 → LoginActivity
          └── 탈퇴하기 → LoginActivity
```

<br>

## 기술 스택

| 분류 | 사용 기술 |
|------|-----------|
| 언어 | Java |
| 최소/타겟 SDK | API 35 / API 36 |
| UI | ConstraintLayout, CardView, LinearLayout, Material Button |
| 지도 | Google Maps Android SDK (`SupportMapFragment`) |
| 위치 | Google Play Services Location (`FusedLocationProviderClient`) |
| 백그라운드 | Android Foreground Service |
| 인증 | Firebase Authentication (Email/Password) |
| 데이터베이스 | Firebase Realtime Database (asia-southeast1) |
| 지오코딩 | Android `Geocoder` API (API 33+ 콜백 방식) |
| 거리 계산 | Haversine Formula |
| 알림 | `NotificationCompat`, `NotificationChannel` |

<br>

## 아키텍처

```
┌─────────────────────────────────────────────┐
│                  Android App                │
│                                             │
│  Activities (UI Layer)                      │
│  ┌──────────┐ ┌──────────┐ ┌─────────────┐ │
│  │ Parent   │ │  Child   │ │   Link /    │ │
│  │ Home     │ │ Activity │ │  Settings   │ │
│  └────┬─────┘ └────┬─────┘ └─────────────┘ │
│       │             │                       │
│  ┌────▼─────────────▼──────────────────┐    │
│  │      Firebase Realtime Database      │    │
│  │  (위치 읽기 / 설정 읽기·쓰기)        │    │
│  └──────────────────────────────────────┘    │
│                                             │
│  ┌──────────────────────────────────────┐   │
│  │         LocationService              │   │
│  │  (Foreground Service)                │   │
│  │  FusedLocationProviderClient         │   │
│  │  → Firebase location/{uid} 업로드    │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
         │                        ▲
         ▼                        │
┌─────────────────┐     ┌──────────────────┐
│ Firebase Auth   │     │  Google Maps SDK │
│ (로그인/가입)   │     │  (지도 렌더링)   │
└─────────────────┘     └──────────────────┘
```

### 지오펜스 동작 방식

```
LocationService 위치 업데이트 (10초)
        │
        ▼
startLocationListener() — Firebase에서 자녀 위치 수신
        │
        ▼
checkGeofence(childUid, lat, lng)
        │
        ├── for each registeredLocation:
        │       haversineDistance(child, location)
        │
        ├── distance ≤ 300m  →  inZone = true
        └── distance > 300m  →  inZone = false
                │
                ├── outZone → inZone : "도착" 알림
                └── inZone → outZone : "이탈" 알림
                        → sendGeofenceNotification()

위치 미수신 감지
        마지막 수신 후 5분 경과 → "기기 꺼진 것 같아요" 알림
        새 위치 수신 시 타이머 리셋

SOS 흐름
        아이가 SOS 버튼 → Firebase sos/{childUid} 쓰기
                → 부모 앱에서 ValueEventListener 감지
                → PRIORITY_MAX 알림 즉시 발송
```

<br>

## Firebase 데이터베이스 구조

```
pinkid-realtime-db/
│
├── users/
│   └── {uid}/
│       ├── name        : String          # 사용자 이름
│       ├── email       : String          # 이메일
│       ├── role        : "parent"|"child"
│       │
│       │   ── 보호자 전용 ──
│       ├── children/
│       │   └── {childUid} : true         # 연결된 자녀 UID 목록
│       └── registeredLocations/
│           └── {locationKey}/
│               ├── nickname  : String    # 별명 (집, 학교 등)
│               ├── address   : String    # 주소 텍스트
│               ├── latitude  : Double    # 위도 (지오코딩)
│               └── longitude : Double    # 경도 (지오코딩)
│
│       │   ── 자녀 전용 ──
│       └── linkedWith : String           # 연결된 보호자 UID
│
├── location/
│   └── {childUid}/
│       ├── latitude  : Double            # 현재 위도
│       ├── longitude : Double            # 현재 경도
│       └── timestamp : Long             # 마지막 업데이트 시각 (ms)
│
├── child_codes/
│   └── {6자리코드} : childUid            # 아이 코드 조회 테이블 (영구)
│
└── sos/
    └── {childUid}/
        ├── timestamp : Long              # SOS 발생 시각 (ms)
        └── childName : String            # 아이 이름
```

<br>

## 프로젝트 파일 구조

```
app/src/main/
│
├── java/com/inhatc/pinkid/
│   ├── SplashActivity.java          # 앱 진입점 — 자동 로그인 라우팅
│   ├── LoginActivity.java           # 로그인
│   ├── RegisterActivity.java        # 회원가입 (역할 선택 포함)
│   ├── LinkActivity.java            # 보호자↔자녀 연결 코드 화면
│   │
│   ├── ParentHomeActivity.java      # 보호자 홈 (미니맵, 지오펜스)
│   ├── ParentMapActivity.java       # 보호자 전체화면 지도
│   ├── SettingsActivity.java        # 보호자 설정
│   ├── LocationRegisterActivity.java # 안전 구역 등록·관리
│   │
│   ├── ChildActivity.java           # 자녀 홈 (본인 위치 미니맵)
│   ├── ChildSettingsActivity.java   # 자녀 설정 (부모 재등록 포함)
│   │
│   └── LocationService.java         # Foreground GPS 서비스
│
└── res/
    ├── layout/
    │   ├── activity_splash.xml
    │   ├── activity_login.xml
    │   ├── activity_register.xml
    │   ├── activity_link.xml
    │   ├── activity_parent_home.xml
    │   ├── activity_child.xml
    │   ├── activity_settings.xml
    │   ├── activity_child_settings.xml
    │   └── activity_location_register.xml
    ├── drawable/
    │   ├── border_box.xml           # 녹색 테두리 라운드 박스
    │   ├── rounded_white_bg.xml     # 흰색 라운드 배경
    │   └── circle_back_btn.xml      # 원형 뒤로가기 버튼
    └── values/
        ├── themes.xml               # 앱 테마 (Material Button 스타일)
        └── strings.xml
```

<br>

## 시작하기

### 사전 요구사항

- Android Studio Hedgehog 이상
- JDK 11 이상
- Google 계정 (Firebase 프로젝트용)
- Google Maps API 키

### 설치 방법

**1. 저장소 클론**
```bash
git clone https://github.com/<your-username>/PinKid.git
cd PinKid
```

**2. Firebase 프로젝트 연결**
1. [Firebase Console](https://console.firebase.google.com/)에서 새 프로젝트 생성
2. Android 앱 등록 (패키지명: `com.inhatc.pinkid`)
3. `google-services.json` 다운로드 → `app/` 폴더에 배치
4. Firebase Console에서 아래 항목 활성화:
   - **Authentication** → 이메일/비밀번호 로그인 사용 설정
   - **Realtime Database** → 데이터베이스 생성 (asia-southeast1 권장)

**3. Realtime Database 보안 규칙 설정**
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "location": {
      "$childUid": {
        ".read": "auth != null && (
          auth.uid == $childUid ||
          root.child('users').child(auth.uid).child('children').child($childUid).exists()
        )",
        ".write": "auth != null && auth.uid == $childUid"
      }
    },
    "child_codes": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "sos": {
      "$childUid": {
        ".read": "auth != null && (
          auth.uid == $childUid ||
          root.child('users').child(auth.uid).child('children').child($childUid).exists()
        )",
        ".write": "auth != null && auth.uid == $childUid"
      }
    }
  }
}
```

**4. Google Maps API 키 설정**
1. [Google Cloud Console](https://console.cloud.google.com/)에서 **Maps SDK for Android** 활성화
2. API 키 발급 후 `AndroidManifest.xml`의 아래 항목에 입력:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_MAPS_API_KEY" />
```

**5. 빌드 & 실행**
```
Android Studio에서 프로젝트 열기 → Sync Gradle → Run
```

> ⚠️ **주의**: `google-services.json` 및 Maps API 키는 보안상 `.gitignore`에 포함하거나 별도 환경변수로 관리하세요.

<br>

## 필요 권한

| 권한 | 용도 |
|------|------|
| `ACCESS_FINE_LOCATION` | GPS 기반 정밀 위치 수집 |
| `ACCESS_COARSE_LOCATION` | 네트워크 기반 대략적 위치 수집 |
| `FOREGROUND_SERVICE` | 백그라운드 위치 서비스 실행 |
| `FOREGROUND_SERVICE_LOCATION` | 포그라운드 서비스 위치 타입 선언 |
| `INTERNET` | Firebase 통신 |
| `POST_NOTIFICATIONS` | 지오펜스 이탈 알림 발송 (Android 13+) |

<br>

## 팀원

| 역할 | 이름 |
|------|------|
| 개발 | 김도윤 |
| 개발 | 손혜지 |

> 인하공업전문대학 모바일 프로그래밍 수업 기말 프로젝트

<br>

---

<p align="center">
  <b>PinKid</b> — 우리 아이가 있는 곳, 언제나 함께 📍
</p>
