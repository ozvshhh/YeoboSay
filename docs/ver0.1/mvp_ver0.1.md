# YeoboSay MVP ver0.1 개발기획서

## 1. 목적

YeoboSay MVP ver0.1의 목적은 Android 앱 안에서 사용자가 음성으로 AI와 대화할 수 있는 최소 기능을 구현하는 것이다.

이 버전은 실제 전화번호 기반 통화가 아니라 앱 내부 음성 대화 방식으로 구현한다. 사용자는 Android 앱에서 통화 화면을 열고, 말하기 버튼을 눌러 음성을 녹음하고, 백엔드는 OpenAI API를 사용해 음성을 텍스트로 변환하고 답변을 생성한 뒤 다시 음성으로 변환해 Android 앱에 전달한다.

ver0.1은 구현 속도와 안정성을 우선해 HTTP REST 기반 발화 단위 처리로 구현한다. WebRTC, WebSocket, OpenAI Realtime API는 직접 구현하지 않지만, 이후 버전에서 실시간 통화 구조를 추가할 수 있도록 통화 세션, 대화 턴, 음성 처리 서비스를 분리해서 설계한다.

## 2. MVP 범위

### 포함하는 기능

- Android 앱 내부 AI 음성 대화
- 통화 세션 생성 및 종료
- 사용자 음성 녹음
- 백엔드로 오디오 업로드
- OpenAI STT를 통한 음성 텍스트 변환
- OpenAI 응답 생성
- OpenAI TTS를 통한 음성 응답 생성
- Android 앱에서 AI 음성 응답 재생
- Android 앱에서 테스트용 대화 텍스트 표시
- 대화 세션 및 대화 턴 저장
- 대화 세션 및 턴 조회
- 위험 발화 감지 결과 저장
- 기본 오류 처리
- Swagger API 문서화

### 포함하지 않는 기능

- 실제 전화번호 기반 통화
- Twilio 등 외부 전화망 연동
- WebRTC
- WebSocket 실시간 통화
- OpenAI Realtime API
- 스트리밍 STT/TTS
- 사용자 회원가입
- 로그인/JWT 인증
- 보호자 관리
- 푸시 알림
- 결제
- 운영 배포 자동화

## 3. 전체 동작 흐름

```text
Android 앱 실행
→ 통화 시작 버튼 클릭
→ Backend에 통화 세션 생성 요청
→ Backend가 sessionId 반환
→ 사용자가 말하기 버튼을 누르고 발화
→ Android가 음성을 파일로 녹음
→ Android가 음성 파일을 Backend에 multipart/form-data로 업로드
→ Backend가 OpenAI STT로 사용자 음성을 텍스트 변환
→ Backend가 사용자 발화를 DB에 저장
→ Backend가 최근 대화 맥락과 시스템 프롬프트로 OpenAI 응답 생성
→ Backend가 AI 응답을 DB에 저장
→ Backend가 OpenAI TTS로 응답 음성 생성
→ Backend가 Android에 userText, assistantText, audioBase64 반환
→ Android가 AI 음성을 재생
→ Android가 테스트용 대화 기록을 화면에 표시
→ 사용자가 다시 말하기
→ 통화 종료 버튼 클릭
→ Backend에 세션 종료 요청
```

## 3.1 ver0.1 확정 결정

- 실제 전화번호 통화는 제외한다.
- WebRTC는 제외한다.
- OpenAI Realtime API는 제외한다.
- Android와 Backend 연결은 HTTP REST API를 사용한다.
- 오디오는 발화 단위 파일 업로드 방식으로 처리한다.
- 음성 턴 응답은 JSON + `audioBase64`로 반환한다.
- Android 녹음 포맷은 `.m4a`를 사용한다.
- Backend 업로드 MIME type은 `audio/mp4`를 우선 허용한다.
- AI 응답 재생 중 사용자의 말하기는 ver0.1에서 막는다.
- 통화 시간 제한은 10분이다.
- 발화 녹음 시간 제한은 30초이다.
- 0.5초 미만 녹음은 Android에서 업로드하지 않는다.
- Android UI는 Jetpack Compose로 구현한다.
- 회원가입과 로그인은 제외하고 익명 세션으로 처리한다.
- DB에는 음성 파일을 저장하지 않고 텍스트와 위험 감지 정보만 저장한다.
- 일반 발화 전문은 서버 로그에 남기지 않는다.
- 위험 발화는 서버 로그에 감지 여부와 발화 내용을 남긴다.
- 첫 AI 인사말 기본값은 "안녕하세요. 저는 YeoboSay 말벗이에요. 오늘은 어떻게 지내셨어요?"로 한다.

## 3.2 이후 실시간 통화 확장성

ver0.1은 WebRTC와 Realtime API를 구현하지 않지만, 다음 확장을 고려한다.

- `CallSession`은 향후 REST 세션과 Realtime 세션이 모두 사용할 수 있는 공통 세션으로 유지한다.
- 음성 처리 로직은 `VoiceTurnService` 같은 서비스로 분리해 REST 업로드 방식과 Realtime 방식이 같은 대화 저장 로직을 재사용할 수 있게 한다.
- OpenAI 호출은 `OpenAiService` 뒤에 숨겨 STT/TTS/응답 생성 구현을 추후 Realtime API 구현으로 교체하거나 병렬 추가할 수 있게 한다.
- Android UI 상태는 `Recording`, `Uploading`, `Playing` 외에 추후 `ConnectingRealtime`, `Streaming`, `Interrupted` 같은 상태를 추가할 수 있게 설계한다.
- Android 화면은 Compose 상태 기반 UI로 작성해 추후 Realtime 상태를 추가하기 쉽게 유지한다.

## 4. Backend 구현 기능

### 4.1 환경 설정

Backend는 다음 환경변수를 사용한다.

```env
DATABASE_URL="postgresql://yeobosay:yeobosay@localhost:5432/yeobosay"
OPENAI_API_KEY="OpenAI API key"
PORT=3000
NODE_ENV=development
```

필요 작업:

- `backend/.env.example`에 `OPENAI_API_KEY` 항목 추가
- `ConfigModule`을 통해 환경변수 접근
- `OPENAI_API_KEY`가 없을 때 명확한 오류 메시지 제공
- 실제 `.env` 파일은 커밋하지 않음

### 4.2 OpenAI 연동 모듈

Backend에 OpenAI API 호출을 담당하는 모듈을 추가한다.

권장 구조:

```text
backend/src/openai/
  openai.module.ts
  openai.service.ts
```

필요 기능:

- OpenAI client 생성
- STT 요청 함수
- 응답 생성 함수
- TTS 요청 함수
- OpenAI API 오류 처리
- 요청 제한과 파일 크기 제한에 대한 예외 처리

### 4.3 Prisma DB 모델

MVP ver0.1에서는 최소한 통화 세션과 대화 턴을 저장한다.

권장 모델:

```prisma
enum CallSessionStatus {
  ACTIVE
  ENDED
}

enum ConversationRole {
  USER
  ASSISTANT
}

model CallSession {
  id        String            @id @default(cuid())
  status    CallSessionStatus @default(ACTIVE)
  startedAt DateTime          @default(now())
  endedAt   DateTime?
  expiresAt DateTime
  turns     ConversationTurn[]
}

model ConversationTurn {
  id            String           @id @default(cuid())
  callSessionId String
  role          ConversationRole
  text          String
  failed        Boolean          @default(false)
  riskFlag      Boolean          @default(false)
  riskType      String?
  createdAt     DateTime         @default(now())

  callSession   CallSession      @relation(fields: [callSessionId], references: [id])

  @@index([callSessionId])
}
```

필요 작업:

- Prisma schema 수정
- Prisma migration 생성
- `PrismaService`를 통해서만 DB 접근
- 컨트롤러에서 Prisma 직접 사용 금지
- 통화 세션 생성 시 `expiresAt`은 `startedAt + 10분`으로 저장

### 4.4 통화 세션 API

#### 세션 생성

```http
POST /call-sessions
```

응답 예시:

```json
{
  "id": "call-session-id",
  "status": "ACTIVE",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": null,
  "expiresAt": "2026-05-16T05:10:00.000Z"
}
```

Backend 처리:

- `CallSession` 생성
- 상태는 `ACTIVE`
- `id`, `status`, `startedAt`, `endedAt`, `expiresAt` 반환

#### 세션 종료

```http
POST /call-sessions/:id/end
```

응답 예시:

```json
{
  "id": "call-session-id",
  "status": "ENDED",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": "2026-05-16T05:10:00.000Z"
}
```

Backend 처리:

- 세션 존재 여부 확인
- 이미 종료된 세션이면 `409 Conflict` 반환
- `status`를 `ENDED`로 변경
- `endedAt` 저장

#### 세션 조회

```http
GET /call-sessions/:id
```

Backend 처리:

- 세션이 없으면 `404`
- 세션 상태와 시작/종료/만료 시각 반환

#### 대화 턴 목록 조회

```http
GET /call-sessions/:id/turns
```

Backend 처리:

- 세션이 없으면 `404`
- 해당 세션의 대화 턴을 `createdAt` 오름차순으로 반환
- Android 테스트 화면에서 사용자 발화와 AI 응답 텍스트를 표시할 때 사용

### 4.5 음성 턴 API

```http
POST /call-sessions/:id/turns/audio
Content-Type: multipart/form-data
```

요청 필드:

```text
audio: 음성 파일
```

권장 음성 파일 형식:

- Android MVP: `m4a`
- Backend ver0.1 허용 MIME type: `audio/mp4`
- 개발 테스트용 추가 MIME type은 ver0.1 구현에서 열지 않는다.

응답 예시:

```json
{
  "callSessionId": "call-session-id",
  "userText": "오늘 기분이 조금 외로워",
  "assistantText": "그랬군요. 오늘은 어떤 일이 있으셨는지 천천히 이야기해 주셔도 괜찮아요.",
  "audioMimeType": "audio/mpeg",
  "audioBase64": "base64-encoded-audio",
  "failed": false,
  "riskFlag": false,
  "riskType": null
}
```

Backend 처리 순서:

1. 세션 ID 확인
2. 세션 상태가 `ACTIVE`인지 확인
3. 세션이 만료되지 않았는지 확인
4. 업로드 파일 존재 여부 확인
5. 파일 크기 및 MIME type 검증
6. OpenAI STT 호출
7. 위험 발화 키워드 감지
8. 사용자 발화 `ConversationTurn` 저장
9. 최근 대화 턴 조회
10. 시스템 프롬프트와 최근 대화 맥락으로 OpenAI 응답 생성
11. AI 응답 `ConversationTurn` 저장
12. OpenAI TTS 호출
13. `userText`, `assistantText`, `audioBase64`, `failed`, `riskFlag`, `riskType` 반환

OpenAI 응답 생성이나 TTS가 실패하면 세션 상태는 `ACTIVE`로 유지한다. 이 경우 응답 JSON은 `failed: true`를 포함하고 Android가 재시도 UI를 표시할 수 있어야 한다.

### 4.6 AI 응답 정책

통화 시작 후 Backend는 첫 AI 인사말을 생성하거나 기본 문구를 사용해 Android에 전달한다.

기본 첫 인사말:

```text
안녕하세요. 저는 YeoboSay 말벗이에요. 오늘은 어떻게 지내셨어요?
```

MVP 시스템 프롬프트 방향:

```text
당신은 고령 사용자를 위한 따뜻하고 차분한 AI 말벗입니다.
짧고 이해하기 쉬운 문장으로 대화합니다.
사용자의 감정을 먼저 공감하고, 한 번에 하나의 질문만 합니다.
의료, 법률, 금융 문제는 전문가에게 상담하도록 안내합니다.
응급 상황으로 보이는 경우 즉시 주변 사람이나 119에 연락하라고 안내합니다.
```

응답 제한:

- 너무 긴 답변 금지
- 1~3문장으로 답변
- 어려운 전문용어 금지
- 사용자를 혼란스럽게 하는 여러 질문 금지
- 한국어 입력에는 한국어로 답변
- 영어 입력에는 영어로 답변
- 의료 진단, 약 복용 지시, 법률 판단, 투자 조언 금지

### 4.7 기본 안전 처리

MVP에서 자동 신고나 보호자 알림은 구현하지 않는다. 대신 위험 신호를 감지하면 안전 안내 문구를 응답에 포함한다.

위험 키워드 후보:

- "죽고 싶어"
- "자해"
- "숨을 못 쉬겠어"
- "쓰러졌어"
- "가슴이 아파"
- "119"
- "도와줘"

위험 타입 기본값:

```text
SELF_HARM
MEDICAL_EMERGENCY
EMOTIONAL_DISTRESS
HELP_REQUEST
UNKNOWN
```

MVP 처리:

- 위험 키워드가 포함되면 DB에 `riskFlag`, `riskType` 저장
- 위험 키워드가 포함되면 서버 로그에 위험 발화 감지 여부와 위험 발화 내용을 출력
- AI 응답에는 즉시 주변 사람, 보호자, 119에 연락하라는 안내 포함
- 실제 신고 자동화는 ver0.1 범위에서 제외

### 4.8 Backend 테스트

필수 테스트:

- `POST /call-sessions` 성공 테스트
- `POST /call-sessions/:id/end` 성공 테스트
- `GET /call-sessions/:id` 성공 테스트
- `GET /call-sessions/:id/turns` 성공 테스트
- 없는 세션 종료 시 실패 테스트
- 종료된 세션 재종료 시 `409` 테스트
- `POST /call-sessions/:id/turns/audio`는 OpenAI service mock 기반 테스트
- OpenAI API key 누락 시 서비스 초기화 실패 또는 명확한 예외 테스트

검증 명령:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

## 5. Android 구현 기능

### 5.1 통화 화면

MVP 통화 화면은 고령 사용자가 쓰기 쉬운 단순한 구조로 만든다.

필요 UI:

- 큰 통화 시작 버튼
- 큰 말하기 버튼
- 테스트용 사용자/AI 텍스트 대화 기록 영역
- AI 응답 재생 상태 표시
- 통화 종료 버튼
- 오류 메시지 표시 영역

상태 예시:

```text
Idle: 통화 전
Starting: 세션 생성 중
Ready: 말하기 가능
Recording: 사용자 음성 녹음 중
Uploading: 음성 업로드 및 AI 응답 대기 중
Playing: AI 음성 응답 재생 중
Ending: 통화 종료 중
Error: 오류 발생
```

ver0.1에서는 `Playing` 상태에서 말하기 버튼을 비활성화한다. 최종 버전에서는 AI 음성 재생 중 사용자가 말을 끊는 UX를 목표로 하지만, 해당 기능은 WebRTC 또는 Realtime API 기반 실시간 처리 버전에서 구현한다.

### 5.2 Android 권한

필수 권한:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

필요 작업:

- 마이크 권한 런타임 요청
- 권한 거부 시 통화 시작 제한
- 권한 거부 안내 메시지 표시
- 네트워크 연결 실패 시 재시도 안내

### 5.3 세션 생성 연동

Android에서 통화 시작 버튼을 누르면 Backend에 세션 생성 요청을 보낸다.

```http
POST /call-sessions
```

Android 처리:

- 요청 성공 시 `sessionId` 저장
- 상태를 `Ready`로 변경
- 실패 시 `Error` 상태 표시

### 5.4 음성 녹음

MVP는 실시간 스트리밍이 아니라 발화 단위 녹음 파일 업로드 방식을 사용한다.

권장 방식:

- 말하기 버튼 누름: 녹음 시작
- 말하기 버튼 다시 누름: 녹음 종료
- 녹음 파일을 임시 파일로 저장
- 파일 형식은 우선 `m4a` 사용
- 발화 최대 길이는 30초
- 0.5초 미만 녹음은 업로드하지 않음

구현 후보:

- Jetpack Compose
- Android `MediaRecorder`
- 출력 포맷: MPEG_4
- 오디오 인코더: AAC
- 파일 확장자: `.m4a`
- Android 최소 지원 버전은 `minSdk 26`을 권장

주의 사항:

- 너무 짧은 녹음은 업로드하지 않음
- 녹음 중 통화 종료 버튼 처리 필요
- 녹음 실패 시 임시 파일 삭제

### 5.5 음성 업로드 연동

녹음이 끝나면 Backend에 multipart 요청을 보낸다.

```http
POST /call-sessions/:id/turns/audio
Content-Type: multipart/form-data
```

요청 필드:

```text
audio: 녹음 파일
```

Android 처리:

- 상태를 `Uploading`으로 변경
- 응답 JSON 파싱
- `audioBase64`를 오디오 파일로 저장하거나 메모리에서 재생 준비
- ver0.1 구현은 앱 내부 cache directory에 임시 파일로 저장한 뒤 재생
- 실패 시 오류 표시

### 5.6 AI 음성 재생

Backend 응답의 `audioBase64`를 재생한다.

Android 처리:

- Base64 decode
- 임시 오디오 파일 저장
- `MediaPlayer`로 재생
- 재생 중에는 말하기 버튼 비활성화
- 재생 완료 후 상태를 `Ready`로 변경

주의 사항:

- 재생 중 통화 종료 버튼을 누르면 재생 중지
- 다음 녹음 시작 전 이전 재생 리소스 해제
- 임시 오디오 파일 정리

### 5.7 통화 종료 연동

통화 종료 버튼을 누르면 Backend에 종료 요청을 보낸다.

```http
POST /call-sessions/:id/end
```

Android 처리:

- 녹음 중이면 녹음 중지
- 재생 중이면 재생 중지
- 종료 API 호출
- `sessionId` 초기화
- 상태를 `Idle`로 변경

### 5.8 Android 네트워크 설정

로컬 개발 환경:

- Android Emulator에서 Mac의 localhost 접근: `http://10.0.2.2:3000`
- 실제 Android 기기에서 Mac 접근: 같은 Wi-Fi에서 Mac의 로컬 IP 사용

예시:

```text
Emulator base URL: http://10.0.2.2:3000
Physical device base URL: http://192.168.0.xxx:3000
```

실제 기기 테스트 시 필요 조건:

- Mac과 Android 기기가 같은 네트워크에 있어야 함
- macOS 방화벽이 3000번 포트 연결을 막지 않아야 함
- Backend가 `localhost`가 아닌 모든 인터페이스에서 접근 가능해야 함

## 6. Backend와 Android 연결 방식

### 6.1 MVP 연결 방식

MVP에서는 HTTP REST API를 사용한다.

선택 이유:

- 구현이 빠름
- 디버깅이 쉬움
- Swagger로 API 확인 가능
- WebSocket보다 Android 구현 부담이 낮음
- 발화 단위 음성 대화에 적합

### 6.2 API 목록

```http
GET /health
POST /call-sessions
GET /call-sessions/:id
GET /call-sessions/:id/turns
POST /call-sessions/:id/turns/audio
POST /call-sessions/:id/end
```

### 6.3 오디오 응답 방식

MVP에서는 JSON 응답에 `audioBase64`를 포함한다.

장점:

- Android에서 응답 처리 단순
- 텍스트와 음성을 한 번에 받을 수 있음
- 별도 파일 저장소나 정적 파일 서빙이 필요 없음

단점:

- 응답 크기가 커짐
- 긴 음성에는 비효율적

ver0.1 이후 개선 방향:

- 오디오 바이너리 직접 반환
- 임시 오디오 파일 URL 반환
- 스트리밍 TTS 적용

## 7. 필요한 외부 정보와 권한

### 7.1 OpenAI

필요 정보:

- OpenAI API key
- 사용할 STT 모델: 구현 시점 공식 문서 기준으로 선택, 기본 후보 `gpt-4o-mini-transcribe`
- 사용할 응답 생성 모델: 구현 시점 공식 문서 기준으로 선택
- 사용할 TTS 모델: 구현 시점 공식 문서 기준으로 선택, 기본 후보 `gpt-4o-mini-tts`
- 사용할 TTS voice: 여성 톤 voice 우선 선택

환경변수:

```env
OPENAI_API_KEY=
```

주의:

- API key는 `.env`에만 저장
- GitHub에 커밋 금지
- CI에서는 GitHub Actions secret으로 관리

### 7.2 PostgreSQL

로컬 개발 정보:

```env
DATABASE_URL="postgresql://yeobosay:yeobosay@localhost:5432/yeobosay"
```

Docker Compose:

- DB 이름: `yeobosay`
- 사용자: `yeobosay`
- 비밀번호: `yeobosay`
- 포트: `5432`

### 7.3 Android 권한

필수:

- `RECORD_AUDIO`
- `INTERNET`

선택 후보:

- 로컬 파일 저장을 외부 저장소에 하지 않는다면 저장소 권한은 필요 없음
- MVP에서는 앱 내부 cache directory에 임시 오디오 파일 저장 권장

## 8. 에러 처리 기준

Backend:

- 세션이 없으면 `404`
- 종료된 세션에 음성 업로드하면 `409`
- 이미 종료된 세션 종료 요청은 `409`
- 만료된 세션에 음성 업로드하면 `409`
- 음성 파일이 없으면 `400`
- 허용되지 않은 파일 형식이면 `400`
- OpenAI STT 실패는 `502`
- OpenAI 응답 생성 실패는 세션을 유지하고 `failed: true` 응답 반환
- OpenAI TTS 실패는 세션을 유지하고 `failed: true` 응답 반환
- 알 수 없는 서버 오류는 `500`

Android:

- 마이크 권한 없음: 권한 요청 또는 안내
- 녹음 실패: 다시 시도 안내
- 네트워크 실패: 연결 확인 안내
- Backend 오류: 짧은 오류 메시지 표시
- AI 응답 실패: 다시 말하기 유도

## 9. 권장 PR 분리

### PR 1: Backend 세션/DB 기반

제목:

```text
[BE] Add call session schema and APIs
```

범위:

- Prisma 모델
- migration
- `CallSessionsModule`
- 세션 생성 API
- 세션 종료 API
- 세션 조회 API
- 대화 턴 목록 조회 API
- 테스트

### PR 2: Backend OpenAI 음성 턴

제목:

```text
[BE] Add OpenAI voice turn pipeline
```

범위:

- OpenAI module/service
- STT
- AI 답변 생성
- TTS
- 음성 업로드 API
- Swagger multipart 문서화
- OpenAI mock 테스트

### PR 3: Android 통화 화면

제목:

```text
[Android] Add AI call screen UI
```

범위:

- 통화 화면 UI
- 상태 표시
- 권한 요청 UI
- 네트워크 연결 전 화면 상태만 구현

### PR 4: Android 음성 대화 연결

제목:

```text
[Android] Connect AI voice call MVP
```

범위:

- 세션 생성 API 연동
- 음성 녹음
- 음성 업로드
- AI 음성 재생
- 통화 종료 API 연동
- 에러 처리

## 10. ver0.1 완료 기준

MVP ver0.1은 다음 조건을 만족하면 완료로 본다.

- Android 앱에서 통화 시작 가능
- Backend에 통화 세션이 생성됨
- Android에서 음성을 녹음해 Backend로 업로드 가능
- Backend가 사용자 음성을 텍스트로 변환함
- Backend가 AI 답변을 생성함
- Backend가 AI 답변 음성을 생성함
- Android에서 AI 음성을 재생함
- 같은 세션에서 여러 번 대화 가능
- 통화 종료 가능
- 대화 턴이 DB에 저장됨
- Backend 필수 검증 명령이 통과함
- Android 빌드가 통과함

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

Android 검증:

```bash
cd android
./gradlew build
```

## 11. 이후 버전 후보

ver0.1 이후 개선 후보:

- WebSocket 기반 저지연 음성 대화
- OpenAI Realtime API 검토
- WebRTC 기반 Android 실시간 통화 UX
- 실제 전화번호 기반 통화 연동
- 보호자 알림
- 긴급 상황 감지 고도화
- 통화 요약 생성
- 대화 기록 화면
- 사용자 프로필
- 보호자 계정
- 푸시 알림
- 운영 배포
