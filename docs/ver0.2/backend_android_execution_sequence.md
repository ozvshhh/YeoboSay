# YeoboSay ver0.2 Backend + Android Execution Sequence

Status: Draft  
Date: 2026-05-30  
Related:

- `docs/ver0.2/conversational_call_flow_plan.md`
- `docs/ver0.2/conversational_call_api_spec.md`
- `docs/ver0.2/backend_conversational_call_work_breakdown.md`
- `docs/ver0.2/android_conversational_call_work_breakdown.md`

## 1. 목적

이 문서는 ver0.2 자동 통화 기능을 실제 개발 지시 순서대로 정리한 실행 계획이다.

사용자는 아래 순서대로 작업을 지시하면 된다. 각 단계는 가능한 한 독립적으로 빌드/테스트 가능한 단위로 쪼갰다.

특히 Android UI, 에뮬레이터, 실제 갤럭시 기기, 음성 입력/출력처럼 자동 테스트만으로 확인하기 어려운 부분은 **사람 수동 테스트 필수**로 강하게 표시한다.

## 2. 전체 원칙

- 먼저 Backend의 데이터 모델과 API 뼈대를 만든다.
- Android는 API 모델과 화면/상태머신을 먼저 붙인다.
- 오디오 자동 감지는 Android에서 가장 리스크가 크므로 API 업로드와 분리해서 검증한다.
- STT/LLM/TTS는 mock 가능한 구조로 붙이고, OpenAI 실제 호출은 뒤에서 검증한다.
- 각 단계 완료 후 git branch, commit, push, PR 단위로 관리한다.
- Backend와 Android를 같은 PR에 섞지 않는 것을 기본으로 한다.
- 단, 최종 E2E 안정화 단계에서는 양쪽 수정이 같이 필요할 수 있다.

## 3. 실행 단계 요약

| 순서 | 영역 | 작업 | 사람 테스트 |
| --- | --- | --- | --- |
| 1 | Backend | DB/DTO/자동 세션 생성 기반 | 선택 |
| 2 | Android | API 모델/상태머신 기반 | 선택 |
| 3 | Backend | 전화 요청 invitation API 안정화 | 선택 |
| 4 | Android | 수신 전화 화면/테스트 요청 | **필수** |
| 5 | Backend | 자동 turn 업로드 mock endpoint | 선택 |
| 6 | Android | 자동 세션 생성/통화 화면/첫 인사 | **필수** |
| 7 | Android | AudioRecord/RMS 발화 감지 | **강력 필수** |
| 8 | Backend | clientTurnId 중복 처리 | 선택 |
| 9 | Android | 발화 파일 생성/자동 업로드 | **필수** |
| 10 | Backend | STT 연결/빈 STT retry | 선택 |
| 11 | Backend | 대화 단계/LLM/TTS 연결 | 선택 |
| 12 | Android | AI 응답 재생/nextAction 처리 | **필수** |
| 13 | Android | barge-in/무응답 재촉 | **강력 필수** |
| 14 | Backend | 종료 의도/end/summary/socket/log | 선택 |
| 15 | Android | 종료/summary/서버 종료 이벤트 | **필수** |
| 16 | Both | 실제 기기 E2E 안정화 | **강력 필수** |

## 4. 상세 실행 순서

## Step 1. Backend DB/DTO/자동 세션 생성 기반

사용자 지시 예시:

```text
백엔드 Step 1 진행해줘. DB 상태 모델, DTO, auto_conversation 세션 생성까지 구현해줘.
```

포함 작업:

- Prisma schema 확장
- migration 생성
- session/turn 상태 enum 또는 상수 추가
- `CreateCallSessionDto` 확장
- `POST /call-sessions`에서 `mode=auto_conversation` 처리
- `audioPolicy`, `conversationPolicy` 응답 추가
- 첫 인사 assistant turn 저장

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

DB 작업이 있으므로 로컬 DB가 켜져 있으면 추가:

```bash
cd backend
npx prisma migrate dev
```

완료 기준:

- `mode=auto_conversation` 세션 생성 가능
- 10분 만료 시간 설정
- 첫 인사 turn 저장
- 기존 manual session 생성이 깨지지 않음

사람 테스트:

- 선택 사항
- Postman/curl로 `POST /call-sessions` 호출 확인 가능

## Step 2. Android API 모델/상태머신 기반

사용자 지시 예시:

```text
안드로이드 Step 2 진행해줘. API DTO랑 통화 상태머신 골격부터 만들어줘.
```

포함 작업:

- API request/response DTO 추가
- `CallUiState`
- `CallScreenState`
- `ConversationMessage`
- 상태 전환 로직
- 기존 첫 화면 유지

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

완료 기준:

- 앱 빌드 성공
- UI가 상태 모델을 기준으로 렌더링할 준비가 됨
- 서버가 꺼져 있어도 앱 crash 없음

사람 테스트:

- 선택 사항
- Android Studio에서 앱 실행 후 첫 화면 유지 확인

## Step 3. Backend 전화 요청 Invitation API 안정화

사용자 지시 예시:

```text
백엔드 Step 3 진행해줘. call-invitations test/accept/decline 상태 전환 안정화해줘.
```

포함 작업:

- `POST /call-invitations/test`
- `POST /call-invitations/:id/accept`
- `POST /call-invitations/:id/decline`
- 상태 전환 검증
- 이미 처리된 invitation에 `409`
- 없는 invitation에 `404`

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- invitation 생성/수락/거절 API 안정화
- Android 테스트 전화 요청 버튼이 붙을 준비 완료

사람 테스트:

- 선택 사항
- curl/Postman으로 API 확인 가능

## Step 4. Android 수신 전화 화면/테스트 요청

사용자 지시 예시:

```text
안드로이드 Step 4 진행해줘. 테스트 전화 요청 버튼, Socket 수신 이벤트, 수신 전화 화면까지 구현해줘.
```

포함 작업:

- 첫 화면 `테스트 전화 요청`
- `POST /call-invitations/test`
- Socket.IO `/call-invitations`
- `incoming_call` 이벤트 처리
- 수신 전화 화면
- 받기/거절 버튼 UI

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

Backend 필요:

```bash
cd backend
docker compose up -d
npx prisma migrate dev
npm run start
```

**사람 수동 테스트 필수**

테스트 시나리오:

1. Android Studio에서 앱 실행
2. Backend 실행
3. 첫 화면에서 `테스트 전화 요청` 클릭
4. 수신 전화 화면이 뜨는지 확인
5. 받기/거절 버튼 위치와 크기 확인
6. 서버 미실행 상태에서도 앱이 crash하지 않는지 확인

통과 기준:

- 수신 전화 화면이 실제 전화처럼 자연스럽게 보임
- 큰글자 UI가 유지됨
- 전화 받기 버튼은 왼쪽에 있음
- 서버 오류가 사용자에게 표시됨

## Step 5. Backend 자동 Turn 업로드 Mock Endpoint

사용자 지시 예시:

```text
백엔드 Step 5 진행해줘. auto-turns/audio endpoint를 mock 응답까지 구현해줘.
```

포함 작업:

- `POST /call-sessions/:id/auto-turns/audio`
- multipart 파일 수신
- `clientTurnId` 검증
- `mode=auto_conversation` 검증
- OpenAI 호출 없이 mock assistant 응답 반환

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- Android가 오디오 업로드 API를 붙일 수 있음
- 아직 STT/TTS가 없어도 응답 구조가 고정됨

사람 테스트:

- 선택 사항
- curl multipart 업로드로 확인 가능

## Step 6. Android 자동 세션 생성/통화 화면/첫 인사

사용자 지시 예시:

```text
안드로이드 Step 6 진행해줘. 전화 받기 후 auto_conversation 세션 만들고 통화 화면에서 첫 인사 재생해줘.
```

포함 작업:

- invitation accept
- `POST /call-sessions`
- `/call-sessions` Socket 연결
- 통화 중 화면 진입
- 첫 인사 재생
- 통화 타이머 시작

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 필수**

테스트 시나리오:

1. 테스트 전화 요청
2. 수신 화면에서 받기 클릭
3. 통화 화면 진입 확인
4. 첫 인사 음성이 재생되는지 확인
5. 통화 타이머가 증가하는지 확인
6. 하단 버튼이 스피커/통화 종료/블루투스인지 확인

통과 기준:

- 사용자가 녹음 버튼을 누르지 않아도 AI가 먼저 인사함
- 통화 화면 UI가 큰글자 화이트 테마 유지

## Step 7. Android AudioRecord/RMS 발화 감지

사용자 지시 예시:

```text
안드로이드 Step 7 진행해줘. AudioRecord로 RMS 기반 발화 감지까지만 구현해줘.
```

포함 작업:

- `RECORD_AUDIO` 권한
- `AudioRecord` 초기화/해제
- RMS 계산
- speech start/end 감지
- debug status 표시
- 아직 서버 업로드는 하지 않아도 됨

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 강력 필수**

테스트 시나리오:

1. 실제 갤럭시 기기 연결
2. 통화 화면 진입
3. 조용히 있을 때 `LISTENING` 유지
4. 말하면 `USER_SPEAKING`으로 바뀌는지 확인
5. 말을 멈추면 발화 종료 감지되는지 확인
6. 작은 목소리와 일반 목소리 모두 테스트
7. 생활 소음에서 오탐지 여부 확인

통과 기준:

- 실제 갤럭시 기기에서 말 시작/끝 감지가 가능
- 에뮬레이터 결과만으로 완료 판단 금지

## Step 8. Backend `clientTurnId` 중복 처리

사용자 지시 예시:

```text
백엔드 Step 8 진행해줘. clientTurnId 중복 처리와 turn 상태 저장을 구현해줘.
```

포함 작업:

- `(sessionId, clientTurnId)` 중복 제약
- 완료된 turn 중복 요청 시 기존 결과 반환
- 처리 중 turn 중복 요청 시 `409`
- turn 상태 저장

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- Android 업로드 재시도에 안전함

사람 테스트:

- 선택 사항

## Step 9. Android 발화 파일 생성/자동 업로드

사용자 지시 예시:

```text
안드로이드 Step 9 진행해줘. 감지된 발화를 파일로 만들고 auto-turns/audio에 업로드해줘.
```

포함 작업:

- 발화 buffer 수집
- m4a/mp4 파일 생성
- `clientTurnId` 생성
- `POST /call-sessions/:id/auto-turns/audio`
- mock assistant 응답 표시

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 필수**

테스트 시나리오:

1. 실제 갤럭시 기기에서 통화 화면 진입
2. 말하기
3. 발화 종료 후 서버 업로드 로그 확인
4. 서버 mock 응답이 Android UI에 표시되는지 확인
5. 네트워크 실패 시 crash 없이 오류 표시 확인

통과 기준:

- 녹음 버튼 없이 발화 파일이 서버에 업로드됨
- 서버 로그에 `clientTurnId`, 파일 크기, MIME 정보가 보임

## Step 10. Backend STT 연결/빈 STT Retry

사용자 지시 예시:

```text
백엔드 Step 10 진행해줘. auto-turns/audio에 STT 연결하고 빈 STT retry 응답 처리해줘.
```

포함 작업:

- OpenAI STT service 연결
- `TRANSCRIBING -> TRANSCRIBED`
- userText 저장
- 빈 STT면 `listen_again`
- STT 실패 구분 저장

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

사람 테스트:

- 선택 사항
- 실제 OpenAI key가 필요한 수동 테스트는 별도 환경에서 수행

완료 기준:

- 빈 음성/인식 실패가 앱 전체 실패로 이어지지 않음

## Step 11. Backend 대화 단계/LLM/TTS 연결

사용자 지시 예시:

```text
백엔드 Step 11 진행해줘. 대화 단계 오케스트레이션, LLM 응답, TTS까지 연결해줘.
```

포함 작업:

- 기본 대화 단계
- 단답 처리
- LLM prompt
- assistantText 저장
- TTS 생성
- audioBase64 반환
- `nextAction` 결정

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- Android가 실제 AI 응답 음성을 재생할 수 있는 응답을 받음

사람 테스트:

- 선택 사항
- OpenAI 실제 호출 수동 확인은 가능하면 한 번 수행

## Step 12. Android AI 응답 재생/nextAction 처리

사용자 지시 예시:

```text
안드로이드 Step 12 진행해줘. 서버 audioBase64 재생하고 nextAction에 따라 상태 전환해줘.
```

포함 작업:

- `audioBase64` decode
- 오디오 재생
- `play_audio`
- `listen_again`
- `end_call_after_audio`
- `force_end`
- 재생 완료 후 자동 청취 복귀

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 필수**

테스트 시나리오:

1. 실제 서버와 Android 연결
2. 사용자 발화 업로드
3. AI 음성 재생 확인
4. 재생 완료 후 자동으로 다시 듣는지 확인
5. STT 빈 결과일 때 다시 말해달라는 안내 확인

통과 기준:

- 서버의 실제 TTS 음성이 앱에서 재생됨
- 사용자가 다음 발화 때 버튼을 누르지 않아도 됨

## Step 13. Android Barge-in/무응답 재촉

사용자 지시 예시:

```text
안드로이드 Step 13 진행해줘. AI 재생 중 barge-in이랑 3초 무응답 재촉 구현해줘.
```

포함 작업:

- AI 재생 중 RMS monitor 유지
- 사용자 발화 감지 시 playback stop
- `bargeIn=true` 업로드
- `LISTENING` 3초 무응답 timer
- 재촉 멘트 재생
- 무한 재촉 방지

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 강력 필수**

테스트 시나리오:

1. AI가 말하는 중 사용자가 말하기
2. AI 음성이 즉시 멈추는지 확인
3. 사용자 발화가 새 turn으로 업로드되는지 확인
4. AI 질문 후 3초간 침묵
5. `여보세요? 제 말 들리세요?` 재촉 재생 확인
6. 재촉 중 말하면 사용자 발화가 우선되는지 확인

통과 기준:

- barge-in이 실제 전화처럼 느껴짐
- 무응답 상황에서 통화가 멈춰 보이지 않음

## Step 14. Backend 종료 의도/end/summary/socket/log

사용자 지시 예시:

```text
백엔드 Step 14 진행해줘. 종료 의도, end API, summary, Socket 이벤트, DemoLog까지 마무리해줘.
```

포함 작업:

- 종료 의도 감지
- `nextAction=end_call_after_audio`
- `POST /call-sessions/:id/end`
- `GET /call-sessions/:id/summary`
- `/call-sessions` Socket events
- session expired/force end
- DemoLog 보강

Backend 검증:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

완료 기준:

- 서버가 통화를 끝낼 수 있음
- Android summary 화면에 필요한 데이터 제공
- 시연 로그가 충분히 찍힘

사람 테스트:

- 선택 사항
- 서버 로그 육안 확인은 권장

## Step 15. Android 종료/Summary/서버 종료 이벤트

사용자 지시 예시:

```text
안드로이드 Step 15 진행해줘. 통화 종료, summary 화면, 서버 종료 이벤트 처리해줘.
```

포함 작업:

- 종료 버튼
- 종료 중 녹음/재생/socket 정리
- `POST /call-sessions/:id/end`
- `GET /call-sessions/:id/summary`
- summary 화면
- `session_force_end`
- `session_expired`
- `session_ended`

Android 검증:

```bash
cd android
./gradlew build
./gradlew test
```

**사람 수동 테스트 필수**

테스트 시나리오:

1. 통화 중 종료 버튼 클릭
2. AI 재생 중 종료 버튼 클릭
3. summary 화면 표시 확인
4. 대화 기록 scroll 확인
5. 서버에서 만료/강제 종료 이벤트 발생 시 앱 종료 흐름 확인

통과 기준:

- 어떤 상태에서도 종료 버튼이 안정적으로 동작
- 통화 종료 후 방금 한 대화 내용을 볼 수 있음

## Step 16. Backend + Android 실제 기기 E2E 안정화

사용자 지시 예시:

```text
ver0.2 E2E 안정화 진행해줘. 실제 갤럭시 기준으로 5턴 자동 통화 흐름 테스트하고 문제 수정해줘.
```

포함 작업:

- Backend/Android 통합 이슈 수정
- 실제 갤럭시 기기 기준 RMS threshold 조정
- TTS 볼륨 확인
- 5턴 대화 흐름 확인
- 위험 신호 로그 확인
- 종료 흐름 확인
- summary 화면 확인
- docs/API 차이 업데이트

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
./gradlew test
```

**사람 수동 테스트 강력 필수**

최종 시나리오:

1. Backend DB 실행
2. Backend 서버 실행
3. 실제 갤럭시 기기에서 앱 실행
4. 테스트 전화 요청
5. 수신 화면에서 받기
6. AI 첫 인사 확인
7. 사용자가 자연스럽게 5턴 대화
8. AI 재생 중 한 번 끼어들기
9. 한 번은 3초 침묵해서 재촉 확인
10. 종료 의도 발화 또는 종료 버튼으로 통화 종료
11. summary 화면 확인
12. 서버 로그 확인

최종 통과 기준:

- 녹음 버튼 없이 5턴 대화 가능
- AI가 먼저 말함
- 사용자가 AI 말을 끊을 수 있음
- 3초 무응답 재촉 동작
- 종료/summary 동작
- 서버 로그로 세션 생성, turn 처리, 답변, 종료 흐름 확인 가능

## 5. 실제 지시 순서만 짧게 보기

아래 순서대로 지시하면 된다.

1. 백엔드 Step 1: DB/DTO/자동 세션 생성 기반
2. 안드로이드 Step 2: API 모델/상태머신 기반
3. 백엔드 Step 3: invitation API 안정화
4. 안드로이드 Step 4: 수신 전화 화면/테스트 요청
5. 백엔드 Step 5: 자동 turn 업로드 mock endpoint
6. 안드로이드 Step 6: 자동 세션 생성/통화 화면/첫 인사
7. 안드로이드 Step 7: AudioRecord/RMS 발화 감지
8. 백엔드 Step 8: clientTurnId 중복 처리
9. 안드로이드 Step 9: 발화 파일 생성/자동 업로드
10. 백엔드 Step 10: STT 연결/빈 STT retry
11. 백엔드 Step 11: 대화 단계/LLM/TTS 연결
12. 안드로이드 Step 12: AI 응답 재생/nextAction 처리
13. 안드로이드 Step 13: barge-in/무응답 재촉
14. 백엔드 Step 14: 종료 의도/end/summary/socket/log
15. 안드로이드 Step 15: 종료/summary/서버 종료 이벤트
16. Both Step 16: 실제 기기 E2E 안정화

## 6. 수동 테스트 게이트

다음 단계는 사람 테스트 없이 완료 처리하면 안 된다.

### Gate A. 수신 전화 UX

해당 단계:

- Step 4

확인:

- 수신 화면 표시
- 받기/거절 버튼 위치
- 큰글자 UI
- 서버 오류 표시

### Gate B. 통화 화면과 첫 인사

해당 단계:

- Step 6

확인:

- 통화 화면 진입
- AI 첫 인사 재생
- 타이머 동작
- 하단 버튼 구성

### Gate C. 실제 기기 음성 감지

해당 단계:

- Step 7
- Step 9

확인:

- 실제 갤럭시 기기에서 말 시작/끝 감지
- 발화 파일 생성
- 서버 업로드

### Gate D. AI 음성 재생

해당 단계:

- Step 12

확인:

- 서버 TTS 음성 재생
- 재생 후 자동 청취 재개
- `listen_again` 처리

### Gate E. 전화다운 상호작용

해당 단계:

- Step 13

확인:

- AI 재생 중 barge-in
- 3초 무응답 재촉
- 재촉 중 사용자 발화 우선 처리

### Gate F. 종료와 Summary

해당 단계:

- Step 15

확인:

- 종료 버튼
- 서버 종료 이벤트
- summary 화면

### Gate G. 최종 실제 기기 E2E

해당 단계:

- Step 16

확인:

- 실제 갤럭시 기기에서 5턴 자동 대화
- barge-in 1회 이상
- 무응답 재촉 1회 이상
- 종료 후 summary
- 서버 데모 로그

## 7. 브랜치 운영 권장

각 단계는 별도 브랜치를 권장한다.

예:

- `feat/backend-auto-session-foundation`
- `feat/android-call-state-foundation`
- `feat/backend-call-invitations`
- `feat/android-incoming-call-flow`
- `feat/backend-auto-turn-upload`
- `feat/android-auto-call-screen`
- `feat/android-audio-detection`
- `feat/backend-turn-idempotency`
- `feat/android-auto-turn-upload`
- `feat/backend-auto-stt`
- `feat/backend-conversation-orchestrator`
- `feat/android-ai-playback`
- `feat/android-barge-in`
- `feat/backend-call-ending-summary`
- `feat/android-call-summary`
- `feat/ver0.2-e2e-stabilization`

브랜치가 너무 많아 부담되면 위 16단계를 PR 5개 정도로 합쳐도 된다. 다만 Android 오디오 감지와 barge-in은 반드시 독립 검증하는 것이 좋다.
