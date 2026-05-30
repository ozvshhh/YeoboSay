# YeoboSay ver0.2 Backend Work Breakdown

Status: Draft  
Date: 2026-05-30  
Related:

- `docs/ver0.2/conversational_call_flow_plan.md`
- `docs/ver0.2/conversational_call_api_spec.md`
- `docs/ver0.2/backend_conversational_call_development_plan.md`

## 1. 목적

이 문서는 ver0.2 백엔드 구현을 테스트 가능한 작은 작업 단위로 나누기 위한 개발 실행 계획이다.

목표는 자동 통화 기능 전체를 한 번에 구현하지 않고, 각 작업 단위가 다음 조건을 만족하게 만드는 것이다.

- 한 PR에서 이해 가능한 범위일 것
- 독립적으로 빌드와 테스트가 가능할 것
- Android 개발자가 중간 산출물을 붙여볼 수 있을 것
- 실패 시 원인 범위가 좁을 것
- DB/API/AI 처리/Socket 이벤트를 단계적으로 확장할 것

## 2. 전체 구현 전략

권장 순서:

1. DB와 DTO를 먼저 안정화한다.
2. `auto_conversation` 세션 생성 API를 먼저 만든다.
3. 자동 turn 업로드 endpoint를 mock 응답으로 먼저 열어 Android 연동 포인트를 만든다.
4. `clientTurnId` 중복 처리로 자동 업로드 안정성을 확보한다.
5. 이후 STT, 대화 단계, LLM, TTS를 순서대로 붙인다.
6. 마지막에 종료 처리, summary, Socket.IO, 데모 로그를 완성한다.

초기 PR 추천 범위:

- 1번 DB 상태 모델 확장
- 2번 DTO/enum/Swagger 정리
- 4번 `auto_conversation` 세션 생성

이 세 작업을 먼저 끝내면 이후 Android와 Backend가 같은 세션 모델을 기준으로 작업할 수 있다.

## 3. 작업 단위

## 3.1 DB 상태 모델 확장

목표:

- 자동 통화 흐름에 필요한 세션/turn 상태를 DB에 명시적으로 저장한다.

주요 변경:

- `CallSession.mode`
- `CallSession.status`
- `CallSession.currentStep`
- `CallSession.endReason`
- `CallSession.riskFlag`
- `CallSession.riskType`
- `ConversationTurn.status`
- `ConversationTurn.clientTurnId`
- `ConversationTurn.conversationStep`
- `ConversationTurn.bargeIn`
- `ConversationTurn.errorCode`
- 필요 시 `CallInvitation.status` 정리

검증:

```bash
cd backend
npx prisma validate
npx prisma migrate dev
npm run build
```

완료 기준:

- Prisma schema가 validate 된다.
- migration이 생성된다.
- 기존 테스트가 깨지지 않는다.
- 기존 수동 통화 흐름의 필드 호환성이 유지된다.

## 3.2 DTO, Enum, Swagger 정리

목표:

- API 입력/응답 타입을 자동 통화 흐름에 맞게 확장한다.

주요 변경:

- `CallSessionMode`
- `CallSessionStatus`
- `ConversationTurnStatus`
- `ConversationStep`
- `AutoTurnNextAction`
- `CreateCallSessionDto`
- `AutoTurnAudioDto`
- `EndCallSessionDto`
- 세션 생성 응답 DTO
- 자동 turn 응답 DTO

검증:

```bash
cd backend
npm run build
npm run test
npm run lint
```

완료 기준:

- DTO validation이 명확하다.
- `mode=auto_conversation`을 받을 수 있다.
- Swagger 문서가 주요 request/response를 설명한다.

## 3.3 Call Invitation 상태 전환 안정화

목표:

- 테스트 전화 요청, 수락, 거절의 상태 전환을 예측 가능하게 만든다.

대상 API:

```http
POST /call-invitations/test
POST /call-invitations/:id/accept
POST /call-invitations/:id/decline
```

주요 변경:

- `RINGING -> ACCEPTED`
- `RINGING -> DECLINED`
- 이미 처리된 invitation에 대한 `409 Conflict`
- 없는 invitation에 대한 `404 Not Found`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- test invitation 생성
- accept 정상 처리
- decline 정상 처리
- accept 후 decline 불가
- decline 후 accept 불가

완료 기준:

- Android 테스트 전화 요청 버튼이 안정적으로 사용할 수 있다.
- 서버 로그에서 invitation 상태 전환을 확인할 수 있다.

## 3.4 `auto_conversation` 세션 생성

목표:

- `POST /call-sessions`에서 자동 통화 세션을 생성한다.

대상 API:

```http
POST /call-sessions
```

Request:

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cminv_001"
}
```

주요 변경:

- `mode=auto_conversation` 처리
- `status=ACTIVE` 또는 초기 상태 저장
- `expiresAt = startedAt + 10분`
- 첫 인사 assistant turn 저장
- `audioPolicy` 응답
- `conversationPolicy` 응답

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- 자동 세션 생성 시 mode 저장
- expiresAt이 10분 뒤로 설정
- 첫 인사 turn 저장
- policy 응답 포함

완료 기준:

- Android가 세션 생성 후 첫 인사/자동 청취 루프를 시작할 수 있다.

## 3.5 자동 Turn 업로드 Endpoint 골격

목표:

- Android가 자동 발화 오디오를 업로드할 수 있는 endpoint를 먼저 연다.
- 이 단계에서는 OpenAI 호출 없이 mock 응답을 반환해도 된다.

대상 API:

```http
POST /call-sessions/:id/auto-turns/audio
Content-Type: multipart/form-data
```

주요 변경:

- multipart 파일 수신
- `clientTurnId` 필수 검증
- `mode=auto_conversation` 검증
- 세션 존재/상태 검증
- 파일 메타데이터 로그
- 임시 assistant 응답 반환

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- 정상 파일 업로드
- `clientTurnId` 누락
- 없는 세션
- 종료된 세션
- 파일 누락

완료 기준:

- Android가 실제 오디오 파일을 endpoint로 보낼 수 있다.
- 서버가 OpenAI 없이도 predictable한 응답을 반환한다.

## 3.6 `clientTurnId` 중복 처리

목표:

- Android 재시도와 네트워크 중복 요청에 안전하게 대응한다.

주요 변경:

- `(sessionId, clientTurnId)` 중복 제약
- 완료된 turn 중복 요청 시 기존 결과 반환
- 처리 중 turn 중복 요청 시 `409 TURN_ALREADY_PROCESSING`

검증:

```bash
cd backend
npm run test
npm run build
npx prisma validate
```

권장 테스트:

- 같은 `clientTurnId`를 2회 업로드
- 첫 요청 완료 후 재요청
- 첫 요청 처리 중 재요청

완료 기준:

- Android가 같은 발화를 재시도해도 중복 대화 turn이 생성되지 않는다.

## 3.7 STT 처리 연결

목표:

- 자동 turn 업로드 파일을 STT로 변환한다.

주요 변경:

- 기존 OpenAI STT service 재사용
- turn 상태 `TRANSCRIBING -> TRANSCRIBED`
- STT 결과 저장
- STT 빈 문자열 처리
- STT 실패 상태 저장

빈 문자열 정책:

- 실패로 처리하지 않는다.
- "다시 한 번 말씀해 주세요" assistant 응답으로 이어간다.
- `nextAction=listen_again`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- STT 정상 결과
- STT 빈 문자열
- STT API 실패

완료 기준:

- userText가 DB에 저장된다.
- 빈 STT 결과가 retry 응답으로 이어진다.

## 3.8 대화 단계 오케스트레이션

목표:

- 기본 안부 체크 대화 순서를 서버에서 관리한다.

기본 단계:

1. `wellbeing`
2. `meal`
3. `health`
4. `medication`
5. `sleep`
6. `schedule`
7. `mood`
8. `free_talk`
9. `closing`

주요 변경:

- 현재 step 계산
- 다음 step 계산
- 단답 처리 정책
- 질문 목적 충족 여부 판단

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- 정상 step 전환
- 단답 후 다음 step 전환
- 불명확 답변 후 한 번 더 질문
- 5턴 완료 후 free talk 또는 closing 판단

완료 기준:

- 서버가 다음 질문 주제를 일관되게 결정한다.

## 3.9 LLM 응답 생성

목표:

- 대화 단계와 사용자 발화를 기반으로 AI 답변을 생성한다.

주요 변경:

- 대화 prompt 구성
- 말벗 톤 고정
- 최근 turn context 포함
- 종료 의도 context 포함
- 위험 신호 context 포함
- turn 상태 `RESPONDING -> RESPONDED`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- OpenAI mock으로 assistantText 저장
- step별 prompt 생성
- LLM 실패 시 `FAILED_LLM`

완료 기준:

- assistantText가 DB에 저장된다.
- 실패 원인이 STT/LLM/TTS 중 LLM으로 구분된다.

## 3.10 TTS 처리 연결

목표:

- assistantText를 Android가 재생 가능한 음성으로 변환한다.

주요 변경:

- TTS 호출
- `audioMimeType`
- `audioBase64`
- turn 상태 `SYNTHESIZING -> COMPLETED`
- TTS 실패 시 `FAILED_TTS`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- TTS 정상 응답
- TTS 실패
- TTS 실패 시 상태 저장

완료 기준:

- Android가 서버 응답 음성을 재생할 수 있다.

## 3.11 종료 의도 처리

목표:

- 사용자가 말로 통화 종료를 요청하면 서버가 자연스럽게 종료 흐름을 만든다.

종료 의도 후보:

- 그만
- 끊어
- 먼저 끊어
- 이제 됐어
- 통화 그만
- 다음에 하자

주요 변경:

- 종료 의도 감지 유틸
- 마무리 assistantText 생성
- `nextAction=end_call_after_audio`
- 세션 상태 `ENDING` 또는 `ENDED`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- "이제 끊어" 입력
- "먼저 끊어라" 입력
- 일반 발화는 종료 처리하지 않음

완료 기준:

- Android가 AI 마무리 멘트 재생 후 통화를 종료할 수 있다.

## 3.12 세션 종료 API 보강

목표:

- 명시적 통화 종료를 안정적으로 처리한다.

대상 API:

```http
POST /call-sessions/:id/end
```

주요 변경:

- `endReason` 저장
- `endedAt` 저장
- 이미 종료된 세션 idempotent 처리
- 진행 중 상태 정리

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- 정상 종료
- 이미 종료된 세션 재종료
- 없는 세션
- 종료 후 turn 업로드 거부

완료 기준:

- Android 종료 버튼이 안정적으로 동작한다.

## 3.13 통화 Summary Endpoint

목표:

- Android가 통화 종료 후 대화 내용을 보여줄 수 있게 한다.

대상 API:

```http
GET /call-sessions/:id/summary
```

주요 변경:

- 세션 정보 반환
- 통화 시간 반환
- turnCount 반환
- riskFlag/riskType 반환
- conversation 목록 반환
- 초기 summaryText는 규칙 기반 생성

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- 종료된 세션 summary
- 진행 중 세션 summary
- turn 없는 세션 summary
- 없는 세션

완료 기준:

- Android summary 화면을 API 응답만으로 구성할 수 있다.

## 3.14 Socket.IO Namespace 추가

목표:

- Android가 수신 전화와 통화 처리 상태를 실시간으로 받을 수 있게 한다.

Namespace:

```text
/call-invitations
/call-sessions
```

주요 이벤트:

- `incoming_call`
- `call_invitation_cancelled`
- `turn_processing_started`
- `turn_transcribed`
- `turn_response_created`
- `turn_completed`
- `turn_failed`
- `session_force_end`
- `session_expired`
- `session_ended`
- `call_summary_ready`

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- Gateway unit test
- event payload shape test
- 수동 Socket.IO 연결 테스트

완료 기준:

- Android가 수신 전화와 통화 상태를 Socket.IO로 받을 수 있다.

## 3.15 DemoLog 정리

목표:

- MVP 시연 중 서버 로그로 흐름을 확인할 수 있게 한다.

환경 변수:

```env
DEMO_LOG_ENABLED=true
DEMO_LOG_TEXT_ENABLED=true
```

로그 대상:

- invitation 생성
- invitation 수락/거절
- session 생성
- auto turn 업로드
- STT 결과
- assistant 답변
- TTS 생성 결과
- risk flag
- session 종료
- summary ready

검증:

```bash
cd backend
npm run test
npm run build
```

권장 테스트:

- log enabled
- log disabled
- text log enabled
- text log disabled

완료 기준:

- `.env`만 바꿔도 데모 로그 출력 여부를 제어할 수 있다.

## 3.16 통합 테스트와 정리

목표:

- mock OpenAI 기반으로 전체 백엔드 흐름을 검증한다.

대상 흐름:

1. invitation 생성
2. invitation accept
3. `auto_conversation` session 생성
4. automatic audio turn upload
5. STT mock
6. LLM mock
7. TTS mock
8. session end
9. summary 조회

검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- 주요 backend CI 명령이 통과한다.
- Android가 붙을 API 흐름이 문서/API/테스트 기준으로 일치한다.

## 4. 추천 PR 분리

### PR 1: DB, DTO, 자동 세션 생성

포함:

- 3.1 DB 상태 모델 확장
- 3.2 DTO/enum/Swagger 정리
- 3.4 `auto_conversation` 세션 생성

이유:

- 이후 모든 작업의 기반이다.
- Android가 세션 생성 응답을 먼저 붙여볼 수 있다.

### PR 2: 자동 turn endpoint와 중복 처리

포함:

- 3.5 자동 turn 업로드 endpoint 골격
- 3.6 `clientTurnId` 중복 처리

이유:

- Android의 자동 녹음/업로드 루프와 병렬 개발이 가능하다.

### PR 3: STT, 대화 단계, LLM, TTS

포함:

- 3.7 STT 처리 연결
- 3.8 대화 단계 오케스트레이션
- 3.9 LLM 응답 생성
- 3.10 TTS 처리 연결

이유:

- AI 처리 단위가 한 흐름으로 검증된다.

### PR 4: 종료, summary, Socket, 로그

포함:

- 3.11 종료 의도 처리
- 3.12 세션 종료 API 보강
- 3.13 통화 summary endpoint
- 3.14 Socket.IO namespace 추가
- 3.15 DemoLog 정리

이유:

- 사용자 경험 완성 요소를 묶어 검증한다.

### PR 5: 통합 테스트와 문서 정리

포함:

- 3.16 통합 테스트와 정리
- Swagger/contract/docs 동기화

이유:

- 실제 구현과 문서/API 계약을 최종 정렬한다.

## 5. 각 작업 완료 시 공통 체크리스트

각 작업 단위 완료 전 확인한다.

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

DB migration이 있는 작업은 추가로 확인한다.

```bash
cd backend
npx prisma migrate dev
```

체크리스트:

- backend 범위 밖 파일을 수정하지 않았다.
- `.env`, `node_modules`, `dist`가 커밋되지 않았다.
- 기존 manual recording API가 깨지지 않았다.
- Swagger 또는 문서가 API 변경과 일치한다.
- 테스트가 OpenAI 실제 호출에 의존하지 않는다.
- 실패 상태가 STT/LLM/TTS 중 어디인지 구분된다.
