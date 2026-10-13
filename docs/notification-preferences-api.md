# 기기별 푸시 알림 ON/OFF API

전체 및 6종별 설정을 서버에 저장하고, Expo 발송 직전에 설정을 확인합니다.
앱이 백그라운드이거나 종료되어 있어도 꺼진 종류는 서버가 새 푸시를 보내지 않습니다.
기존 알림 발생 조건, 로그인 수신 대상, category와 화면 이동 데이터는 유지합니다.

## 설정 단위와 기본값

- 설정은 `installationId` 기준입니다. 같은 계정의 다른 기기에는 자동 적용되지 않습니다.
- 비로그인 기기도 설정할 수 있습니다. 로그인·로그아웃·토큰 갱신으로 초기화되지 않습니다.
- 설정이 없으면 전체 및 6종 모두 `true`입니다. 새로운 installationId는 기본값을 사용합니다.
- `enabled=false`이면 전체 푸시를 차단합니다. 종류별 선택값은 그대로 보존됩니다.
- `enabled=true`일 때는 해당 `categories` 값도 `true`여야 발송합니다.
- 알림함 목록·읽음·미확인 개수는 기존대로 유지합니다. 이 설정은 푸시 발송에 적용됩니다.

| category | 알림 | 기존 수신 대상 |
|---|---|---|
| `NOTICE_MILESTONE` | 일반 공지 조회수 100회 | 로그인·비로그인 활성 기기 |
| `STUDENT_COUNCIL_NOTICE` | 학생회 공지 등록 | 로그인·비로그인 활성 기기 |
| `SYSTEM_NOTICE` | 시스템 공지 등록 | 로그인·비로그인 활성 기기 |
| `CLUB_RECRUITMENT` | 찜한 동아리 모집공고 등록·수정 | 공고 변경 당시 해당 동아리를 찜한 사용자의 활성 기기 |
| `CLASS_REMINDER` | 수업 시작 1시간 전 | 해당 시간표 사용자의 활성 기기 |
| `SHUTTLE_RESERVATION` | 매주 수요일 17:50 셔틀 예매 안내 | 로그인·비로그인 활성 기기 |

개인 알림은 기존처럼 JWT를 포함한 `PUT /api/v1/push-tokens`로 사용자 연결을 해야 수신합니다.
설정을 ON으로 바꿔도 로그인 수신 대상 조건이 바뀌지는 않습니다.

## 요청 공통

Base URL: `/api/v1/notification-preferences`

JWT는 필수가 아닙니다. 기기가 먼저 `PUT /api/v1/push-tokens`로 등록되어 있어야 합니다.
등록된 installationId와 현재 Expo 토큰의 일치 여부를 확인합니다.

| 위치 | 필드 | 필수 | 설명 |
|---|---|---|---|
| query | `installationId` | O | 기존 기기 등록에 사용한 설치 ID, 최대 100자 |
| header | `X-Push-Token` | O | 현재 서버에 등록된 Expo 토큰, 최대 200자 |
| header | `Content-Type` | PATCH 시 O | `application/json` |

토큰은 URL에 넣지 않습니다. 응답은 `Cache-Control: no-store`를 사용합니다.
Expo 토큰이 바뀌면 기존 토큰 등록 API를 먼저 호출한 뒤 새 토큰을 헤더에 사용합니다.

## 설정 조회

```http
GET /api/v1/notification-preferences?installationId={installationId}
X-Push-Token: ExponentPushToken[현재_토큰]
```

응답: `200 OK`

```json
{
  "enabled": true,
  "categories": {
    "NOTICE_MILESTONE": true,
    "STUDENT_COUNCIL_NOTICE": true,
    "SYSTEM_NOTICE": true,
    "CLUB_RECRUITMENT": true,
    "CLASS_REMINDER": true,
    "SHUTTLE_RESERVATION": true
  }
}
```

## 설정 부분 변경

```http
PATCH /api/v1/notification-preferences?installationId={installationId}
X-Push-Token: ExponentPushToken[현재_토큰]
Content-Type: application/json
```

전체 끄기:

```json
{ "enabled": false }
```

전체 다시 켜기(기존 종류별 선택 유지):

```json
{ "enabled": true }
```

셔틀만 끄기:

```json
{ "categories": { "SHUTTLE_RESERVATION": false } }
```

여러 설정을 한 번에 변경:

```json
{
  "enabled": true,
  "categories": {
    "CLASS_REMINDER": true,
    "SHUTTLE_RESERVATION": false
  }
}
```

응답은 조회와 같은 전체 설정 구조이며 `200 OK`입니다. 생략한 값은 유지합니다.
JSON Boolean만 허용합니다. 문자열 `"false"`, 숫자, null, 알 수 없는 종류·필드,
변경 값이 없는 `{}` 또는 `{"categories":{}}`는 400이며 부분 저장하지 않습니다.

| 상태 | 의미 |
|---|---|
| `200` | 조회·변경 성공 |
| `400` | 필수 입력 누락, 길이 초과, 잘못된 JSON·설정값 |
| `403` | 등록되지 않은 installationId 또는 현재 Expo 토큰 불일치 |

오류 응답은 기존 공통 형식입니다.

```json
{
  "status": 403,
  "message": "등록된 기기 정보를 확인할 수 없습니다.",
  "path": "/api/v1/notification-preferences",
  "timestamp": "2026-10-13T00:00:00Z"
}
```

## 프론트 연동 순서

1. installationId를 보존하고, 로컬에 알림 설정이 있으면 Expo 토큰 등록 요청의 `notificationPreferences`에 함께 전달합니다. 최초 동기화를 토큰 등록 후 별도 PATCH로 나누지 않습니다.
2. 등록 성공 후 설정을 조회해 UI에 반영합니다. 초기 로컬 설정은 등록과 함께 저장되어 있으므로 별도 초기 PATCH는 필요 없습니다.
3. 토글 변경 시 변경값만 PATCH하고, 성공 응답의 전체 설정을 UI에 반영합니다.
4. 빠른 연속 조작은 요청 순서를 유지합니다. 실패하면 서버에 저장됐다고 표시하지 말고 재시도 또는 이전값 복구를 제공합니다.
5. 로그아웃 시 기존 토큰 DELETE API는 사용자 연결만 해제합니다. 알림 OFF 용도로 호출하지 않습니다.

OS 알림 권한과 앱 설정은 별개입니다. 서버 설정 ON으로 OS 권한을 켤 수 없습니다.
권한 미허용으로 Expo 토큰이 없으면 앱에 임시 설정을 저장하고, 토큰 발급 후 최초 등록 요청에 함께 전달합니다.
등록 토큰이 있는 기기는 OS 권한을 끈 상태에서도 설정 API를 사용할 수 있습니다.

### 최초 등록 시 설정 함께 저장

`PUT /api/v1/push-tokens`에 선택 필드 `notificationPreferences`를 추가합니다.
로그인 여부와 관계없이 기기 등록과 설정을 같은 트랜잭션에서 저장합니다.
새 기기는 커밋 전 발송 대상에 보이지 않고, 커밋 후에는 함께 저장된 OFF 설정이 적용됩니다.
설정 저장이 실패하면 기기 등록·갱신도 롤백됩니다.

```http
PUT /api/v1/push-tokens
Content-Type: application/json
```

로그인 상태라면 기존처럼 `Authorization: Bearer {accessToken}`도 전달합니다.
등록 요청에는 `X-Push-Token` 헤더가 필요하지 않습니다.

```json
{
  "expoPushToken": "ExponentPushToken[현재_토큰]",
  "projectId": "Expo 프로젝트 ID",
  "platform": "android",
  "installationId": "앱 설치 ID",
  "appVersion": "1.0.0",
  "notificationPreferences": {
    "enabled": false,
    "categories": {
      "NOTICE_MILESTONE": true,
      "STUDENT_COUNCIL_NOTICE": true,
      "SYSTEM_NOTICE": true,
      "CLUB_RECRUITMENT": true,
      "CLASS_REMINDER": true,
      "SHUTTLE_RESERVATION": false
    }
  }
}
```

성공 응답은 기존처럼 `200 OK`, Body 없음입니다.
`notificationPreferences` 내부 규칙은 설정 PATCH와 같으며,
잘못된 Boolean, 알 수 없는 종류·필드, 빈 변경값은 `400`입니다.
잘못된 초기 설정을 전달하면 토큰도 등록·변경하지 않습니다.
로컬 설정이 있으면 전체 enabled와 보관한 6종 값을 함께 보내는 것을 권장합니다.

이 필드를 생략하면 저장된 서버 설정을 유지하며, 설정이 없는 새 설치는 전체 ON입니다.
로그인 및 일반 토큰 갱신에서는 초기 동기화가 완료됐다면 이 필드를 생략합니다.
전달하면 등록 시에도 해당 값이 적용되므로, 오래된 로컬 값을 반복해서 보내 서버의 최신 설정을 덮어쓰지 않도록 합니다.

기존 버전이 이 필드를 보내지 않는 경우까지 초기 로컬 OFF를 서버가 알 수는 없습니다.
최초 동기화 틈을 막으려면 프론트도 첫 등록 요청부터 이 필드를 전달해야 합니다.
이미 등록되어 기본값 ON으로 발송 중인 기기의 푸시는 소급 취소되지 않습니다.

## 발송 적용 범위

설정은 최대 100개 기기의 각 Expo 발송 묶음을 처리하기 직전에 읽습니다.
이미 큐에 들어갔어도 해당 확인 시점에 OFF이면 건너뜁니다.
건너뛴 푸시는 처리 완료되며, 나중에 ON으로 바꿔도 다시 보내지 않습니다.
설정 확인 후 이미 발송 중이거나 Expo/OS에 전달된 푸시는 취소할 수 없습니다.
category가 없는 기존 큐 알림은 type으로 구분하고, 기존 학생회 공지는 제목 규격으로 보완합니다.
테스트·알 수 없는 종류는 개별 토글 없이 전체 enabled만 적용합니다.

## DB 적용

운영은 `ddl-auto=validate`이므로 앱 배포 전에
[증분 SQL](notification-preferences-migration-mysql.sql)을 적용해야 합니다.
대상 DB 확인 → 백업 → SQL 적용 → SHOW CREATE TABLE 및 컬럼 확인 → 앱 배포 순서입니다.
배포 스크립트에도 이 SQL을 포함합니다. 재실행은 저장된 설정을 초기화하지 않습니다.
