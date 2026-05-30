# YeoboSay ver0.2 Android Development Plan

Status: Draft  
Date: 2026-05-30  
Related:

- `docs/ver0.2/conversational_call_flow_plan.md`
- `docs/ver0.2/conversational_call_api_spec.md`

## 1. 목표

ver0.2 Android 개발의 목표는 기존 "녹음 버튼을 눌러 말하고 업로드"하는 MVP 흐름을, 사용자가 실제 전화 통화를 하듯 조작 없이 AI와 대화하는 앱 내부 통화 경험으로 바꾸는 것이다.

이번 범위에서 Android는 다음 책임을 가진다.

- 수신 전화 화면 표시
- 전화 받기/거절 처리
- 통화 세션 생성
- AI 첫 인사 재생
- 자동 마이크 청취
- 사용자 발화 구간 감지
- 발화 단위 오디오 업로드
- AI 응답 음성 재생
- AI 재생 중 사용자 끼어들기(barge-in) 처리
- 통화 종료 처리
- 통화 종료 후 대화 내용 확인 화면 제공

실제 휴대폰 전화 앱 연동(`TelecomManager`, `ConnectionService`)은 이번 범위가 아니다. ver0.2는 앱 내부에서 전화처럼 보이고 동작하는 통화 UX를 구현한다.

## 2. 핵심 사용자 경험

### 2.1 첫 화면

첫 화면은 현재 MVP 화면을 유지한다.

필요한 버튼:

- `테스트 전화 요청`
- 기존 디버그/수동 통화 시작 버튼은 개발 중 유지 가능

첫 화면의 목적:

1. 개발자가 테스트 전화 요청을 보낸다.
2. 백엔드가 수신 전화 이벤트를 발행한다.
3. Android가 수신 화면으로 전환한다.

### 2.2 수신 전화 화면

수신 화면은 삼성 전화 앱의 큰글자 화이트 테마에 가깝게 구성한다.

필수 요소:

- 발신자 이름: `세요`
- 안내 문구: `AI 안부 전화`
- 큰 원형 프로필 영역
- 왼쪽 전화 받기 버튼
- 오른쪽 전화 거절 버튼

접근성 원칙:

- 큰글자 UI 강제 적용
- 버튼 터치 영역을 충분히 크게 유지
- 색상 대비를 높게 유지
- 애니메이션은 과하지 않게 사용

### 2.3 통화 중 화면

통화 중 화면은 현재 개선된 큰글자 화이트 테마를 유지한다.

필수 요소:

- 통화 시간
- 프로필 원형 아이콘
- 이름: `여보세요`
- 설명: `AI 안부 전화`
- 자동 녹음/청취 안내
- 대화 기록 영역
- 하단 버튼 3개:
  - `스피커`
  - `통화 종료`
  - `블루투스`

디버그 상태 문구:

- 개발 빌드에서는 `듣고 있어요`, `답변 준비 중`, `AI 말하는 중` 같은 상태를 표시할 수 있다.
- 실제 서비스 UI에서는 숨길 수 있어야 한다.

### 2.4 통화 종료 후 화면

통화가 끝나면 방금 한 통화 내용을 보여주는 화면으로 이동한다.

필수 요소:

- 통화 시간
- 대화 턴 수
- 위험 신호 여부
- 대화 기록
- 홈으로 돌아가기 버튼

초기 구현에서는 `GET /call-sessions/:id/turns` 또는 `GET /call-sessions/:id/summary` 응답을 그대로 표시해도 된다.

## 3. Android 상태머신

Android 통화 흐름은 명시적인 상태머신으로 관리한다.

| 상태 | 설명 | 주요 진입 조건 |
| --- | --- | --- |
| `IDLE` | 첫 화면 | 앱 시작, 통화 종료 후 홈 |
| `INCOMING` | 수신 전화 화면 | `incoming_call` 수신 |
| `STARTING` | 초대 수락 및 세션 생성 중 | 전화 받기 선택 |
| `AI_GREETING` | 첫 인사 재생 중 | 세션 생성 성공 |
| `LISTENING` | 사용자 발화 대기 | AI 재생 종료, 재시도 후 |
| `NO_RESPONSE_PROMPTING` | 무응답 재촉 멘트 재생 | 3초 무음 |
| `USER_SPEAKING` | 사용자 발화 중 | RMS 임계값 초과 |
| `UPLOADING` | 오디오 업로드 중 | 발화 종료 감지 |
| `AI_PROCESSING` | 서버 처리 대기 | 업로드 완료 후 응답 대기 |
| `AI_PLAYING` | AI 응답 재생 중 | `nextAction=play_audio` |
| `BARGE_IN_DETECTED` | AI 재생 중 사용자 발화 감지 | 재생 중 RMS 임계값 초과 |
| `ENDING` | 종료 처리 중 | 종료 버튼, 음성 종료 의도, 서버 종료 이벤트 |
| `ENDED` | 통화 종료 완료 | `POST /end` 성공 |
| `CALL_SUMMARY` | 통화 내용 확인 | 종료 후 요약 로드 |
| `ERROR` | 복구 가능한 오류 | 네트워크/오디오/권한 오류 |

상태 전환은 ViewModel 또는 단일 CallController 계층에서 중앙 관리한다. UI 컴포넌트가 직접 네트워크/오디오 상태를 조합하지 않게 한다.

## 4. 오디오 구현 전략

### 4.1 권장 방식

초기 구현은 `AudioRecord` 기반으로 진행한다.

이유:

- AI 음성 재생 중에도 마이크 입력을 계속 관찰해야 한다.
- 사용자가 AI 말을 끊으면 즉시 재생을 멈춰야 한다.
- `MediaRecorder`는 완성된 파일 녹음에는 단순하지만, 실시간 발화 감지와 barge-in에는 제약이 크다.

### 4.2 발화 감지

초기 버전은 RMS 기반 감지로 충분하다.

권장 기본값:

| 항목 | 값 |
| --- | --- |
| 샘플레이트 | 16000 Hz |
| 최소 발화 길이 | 500 ms |
| 발화 종료 무음 | 1300 ms |
| 무응답 재촉 시간 | 3000 ms |
| 단일 발화 최대 길이 | 12000 ms |
| 업로드 포맷 | `audio/mp4` 또는 `audio/m4a` |

정확한 RMS 임계값은 실제 갤럭시 기기에서 조정한다. 에뮬레이터 마이크는 참고용으로만 사용한다.

### 4.3 발화 단위 파일 생성

Android는 사용자가 말한 한 발화를 파일로 만든 뒤 업로드한다.

발화 확정 조건:

1. RMS가 임계값 이상으로 올라가면 `USER_SPEAKING`.
2. 이후 일정 시간 이상 무음이면 발화 종료.
3. 최소 발화 길이보다 짧으면 무시.
4. 최대 발화 길이를 넘으면 강제로 발화 종료.
5. `clientTurnId`를 생성하고 파일 업로드.

### 4.4 AI 재생

AI 응답은 서버에서 내려준 `audioBase64`를 파일로 저장하거나 메모리 소스로 변환해 재생한다.

재생 정책:

- 음량은 기본적으로 크게 설정한다.
- 가능하면 통화/미디어 볼륨 설정을 확인한다.
- 재생 중에도 마이크 모니터링은 유지한다.
- 재생 중 사용자 발화가 감지되면 즉시 재생을 중지한다.

### 4.5 Barge-in

AI 재생 중 사용자 발화가 감지되면:

1. 상태를 `BARGE_IN_DETECTED`로 전환한다.
2. 현재 AI 오디오 재생을 중지한다.
3. 사용자 발화를 새 turn으로 녹음한다.
4. 업로드 시 `bargeIn=true`를 포함한다.
5. 서버 응답을 새 AI 응답으로 재생한다.

이 동작은 ver0.2의 핵심 요구사항이다.

## 5. API 연동 계획

### 5.1 테스트 전화 요청

첫 화면 버튼에서 호출:

```http
POST /call-invitations/test
```

응답 성공 후에도 수신 화면은 Socket.IO `incoming_call` 이벤트를 기준으로 표시한다. 테스트 편의를 위해 HTTP 응답의 invitation ID로 바로 수신 화면을 띄우는 fallback은 허용한다.

### 5.2 수신 전화 이벤트

Socket.IO namespace:

```text
/call-invitations
```

이벤트:

```text
incoming_call
call_invitation_cancelled
```

`incoming_call` 수신 시 `INCOMING` 상태로 전환한다.

### 5.3 전화 받기

전화 받기 버튼:

```http
POST /call-invitations/:id/accept
POST /call-sessions
```

세션 생성 request:

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cminv_001"
}
```

세션 생성 성공 후:

1. `/call-sessions?sessionId={id}` Socket.IO 연결
2. 첫 인사 재생
3. 자동 청취 시작

### 5.4 자동 발화 업로드

```http
POST /call-sessions/:id/auto-turns/audio
Content-Type: multipart/form-data
```

필수 필드:

- `audio`
- `clientTurnId`
- `mode=auto_conversation`

권장 필드:

- `startedAt`
- `endedAt`
- `durationMs`
- `mimeType`
- `bargeIn`
- `conversationStep`
- `clientSequence`

응답 처리:

| `nextAction` | Android 동작 |
| --- | --- |
| `play_audio` | AI 오디오 재생 후 `LISTENING` |
| `listen_again` | 재시도 안내 재생 후 `LISTENING` |
| `end_call_after_audio` | 마무리 멘트 재생 후 종료 |
| `force_end` | 즉시 종료 |

### 5.5 서버 상태 이벤트

Socket.IO namespace:

```text
/call-sessions
```

주요 이벤트:

- `turn_processing_started`
- `turn_transcribed`
- `turn_response_created`
- `turn_completed`
- `turn_failed`
- `session_force_end`
- `session_expired`
- `session_ended`
- `call_summary_ready`

Android는 이벤트를 디버그 상태와 로그에 반영한다. 최종 사용자 화면에는 필요한 정보만 보여준다.

### 5.6 통화 종료

종료 버튼 또는 종료 음성 의도 처리 후:

```http
POST /call-sessions/:id/end
```

Android는 다음을 정리한다.

- 오디오 녹음 중지
- AI 재생 중지
- Socket 연결 해제 또는 세션 room 이탈
- summary 화면 이동

## 6. 화면별 구현 계획

### 6.1 첫 화면

현재 앱 첫 화면을 유지한다.

추가/정리할 항목:

- `테스트 전화 요청` 버튼
- 서버 연결 상태 표시
- 개발용 오류 메시지 표시

### 6.2 수신 화면

컴포넌트:

- `IncomingCallScreen`
- `CallerAvatar`
- `AcceptCallButton`
- `DeclineCallButton`

주요 이벤트:

- 받기: invitation accept + session create
- 거절: invitation decline + `IDLE`
- invitation expired: `IDLE`

### 6.3 통화 중 화면

컴포넌트:

- `ActiveCallScreen`
- `CallHeader`
- `ConversationHistory`
- `RecordingNotice`
- `CallControlBar`
- `DebugCallStatus`

하단 버튼:

- 스피커
- 통화 종료
- 블루투스

녹음 버튼은 제거한다. 단, 개발 중 fallback을 위해 숨김 debug action으로 남길 수 있다.

### 6.4 통화 요약 화면

컴포넌트:

- `CallSummaryScreen`
- `SummaryHeader`
- `ConversationTranscriptList`
- `BackToHomeButton`

데이터 우선순위:

1. `GET /call-sessions/:id/summary`
2. fallback: `GET /call-sessions/:id/turns`
3. fallback: 로컬 메모리의 대화 기록

## 7. 로컬 상태 모델

권장 ViewModel state:

```kotlin
data class CallUiState(
    val screen: CallScreenState,
    val invitationId: String? = null,
    val sessionId: String? = null,
    val sessionStatus: String? = null,
    val currentStep: String? = null,
    val elapsedSeconds: Int = 0,
    val isListening: Boolean = false,
    val isAiPlaying: Boolean = false,
    val isUploading: Boolean = false,
    val debugStatusText: String? = null,
    val errorMessage: String? = null,
    val turns: List<ConversationMessage> = emptyList()
)
```

권장 turn 모델:

```kotlin
data class ConversationMessage(
    val role: Role,
    val text: String,
    val createdAt: String? = null,
    val status: String? = null
)
```

## 8. 권한과 기기 정책

필수 권한:

- `RECORD_AUDIO`
- `INTERNET`

권장:

- 오디오 포커스 처리
- 화면 꺼짐 방지 또는 통화 중 wake lock 검토
- 실제 갤럭시 기기 기준 오디오 감도 테스트

권한 거부 시:

- 첫 화면 또는 통화 시작 전 명확한 안내를 보여준다.
- 통화는 시작하지 않는다.

## 9. 오류 처리

### 9.1 네트워크 오류

자동 발화 업로드 실패 시:

1. 같은 `clientTurnId`로 1회 재시도한다.
2. 실패하면 사용자에게 "연결이 잠시 불안정해요. 다시 말씀해 주세요." 안내.
3. 상태를 `LISTENING`으로 복구한다.

### 9.2 STT 빈 결과

서버가 `nextAction=listen_again`을 반환하면:

1. 안내 음성을 재생한다.
2. 같은 질문 단계에서 다시 `LISTENING`.

### 9.3 서버 강제 종료

`session_force_end` 또는 `session_expired` 이벤트 수신 시:

1. 녹음/재생 중지
2. 필요 시 안내 음성 재생
3. `ENDING`
4. summary 화면으로 이동

## 10. 테스트 계획

### 10.1 에뮬레이터 테스트

확인 항목:

- 앱 실행
- 테스트 전화 요청
- 수신 화면 표시
- 전화 받기
- 세션 생성
- AI 첫 인사 재생
- 서버 연결 실패/성공 메시지

에뮬레이터 마이크 성능은 실제 품질 기준으로 삼지 않는다.

### 10.2 실제 갤럭시 기기 테스트

필수 확인 항목:

- 마이크 권한 허용
- 자동 발화 감지
- 무음 3초 재촉
- AI 재생 중 barge-in
- 5턴 연속 대화
- 통화 종료 버튼
- 종료 음성 의도
- 통화 요약 화면

성공 기준:

- 사용자가 녹음 버튼을 누르지 않고 5개 주제 대화를 완료할 수 있다.
- AI가 말하는 중 사용자가 말하면 재생이 중단되고 사용자 발화가 처리된다.
- 통화 종료 후 대화 기록이 표시된다.

## 11. 구현 순서

1. API 클라이언트 DTO 정리
2. Socket.IO 클라이언트 namespace 분리
3. 수신 전화 화면과 첫 화면 연결
4. `auto_conversation` 세션 생성 연동
5. AI 첫 인사 재생
6. `AudioRecord` 기반 RMS 감지 프로토타입
7. 발화 단위 파일 생성
8. `auto-turns/audio` 업로드
9. AI 응답 재생
10. barge-in 처리
11. 무응답 3초 재촉 처리
12. 통화 종료/summary 화면
13. 실제 갤럭시 기기 튜닝

## 12. 비범위

이번 Android 작업에서 제외한다.

- 실제 기본 전화 앱 대체
- 백그라운드 장시간 통화 안정화
- 보호자 알림
- 계정/로그인
- 실시간 음성 스트리밍
- 운영용 오디오 품질 대시보드

## 13. 완료 기준

Android 개발은 다음을 만족하면 ver0.2 MVP 기준으로 완료한다.

1. 테스트 전화 요청으로 수신 화면이 열린다.
2. 전화 받기 후 `auto_conversation` 세션이 생성된다.
3. AI 첫 인사가 자동으로 재생된다.
4. 사용자가 말하면 앱이 자동으로 발화를 감지한다.
5. 발화 오디오가 서버로 업로드된다.
6. 서버 AI 응답 음성이 자동 재생된다.
7. AI 재생 중 사용자 barge-in이 동작한다.
8. 녹음 버튼 없이 5턴 대화가 가능하다.
9. 통화 종료 버튼이 동작한다.
10. 통화 종료 후 대화 내용 확인 화면이 표시된다.
