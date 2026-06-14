# YeoboSay
YeoboSay is an AI-powered voice conversation app designed to support elderly users through natural phone-based interaction. Using real-time speech recognition, LLM-based dialogue, and voice synthesis, the service provides emotional companionship, improves accessibility, and helps reduce loneliness among senior citizens.

## 프로젝트 실행 방법

### 필수 설치

- Git
- Node.js 22 이상 권장
- npm
- Docker Desktop
- Android Studio
- Android Emulator 또는 실제 Android 기기

Android 빌드에 필요한 JDK는 Android Studio에 포함된 버전을 사용해도 됩니다.

### 프로젝트 내려받기

```bash
git clone https://github.com/ozvshhh/YeoboSay.git
cd YeoboSay
git checkout main
git pull
```

특정 기능 브랜치로 시연해야 한다면 해당 브랜치로 이동합니다.

```bash
git checkout <branch-name>
git pull
```

### 백엔드 실행

```bash
cd backend
npm install
cp .env.example .env
```

`backend/.env`에 OpenAI API Key를 설정합니다.

```env
OPENAI_API_KEY=your_openai_api_key
```

Docker Desktop을 실행한 뒤 PostgreSQL 컨테이너를 켭니다.

```bash
docker compose up -d
npx prisma migrate dev
npm run start
```

정상 실행 확인:

```bash
curl http://localhost:3000/health
```

### Android 앱 실행

1. Android Studio에서 `android/` 디렉토리를 엽니다.
2. Gradle Sync가 끝날 때까지 기다립니다.
3. Emulator 또는 실제 Android 기기를 선택합니다.
4. `app` Run Configuration으로 앱을 실행합니다.

Android Emulator에서 로컬 백엔드에 접근할 때는 `localhost`가 아니라 아래 주소를 사용합니다.

```text
http://10.0.2.2:3000
```

실제 Android 기기로 테스트할 때는 `10.0.2.2`를 사용할 수 없습니다. 같은 Wi-Fi에 연결한 뒤 PC의 로컬 IP를 사용하거나 ngrok 같은 터널링 도구를 사용해야 합니다.

예시:

```text
http://192.168.0.12:3000
```

또는:

```bash
ngrok http 3000
```

### 데모 시연 순서

1. Docker Desktop을 실행합니다.
2. 백엔드를 실행합니다.

```bash
cd backend
docker compose up -d
npx prisma migrate dev
npm run start
```

3. Android Studio에서 앱을 실행합니다.
4. 앱 첫 화면에서 `테스트 전화 요청`을 누릅니다.
5. 전화 수신 화면이 표시되는지 확인합니다.
6. `받기`를 누릅니다.
7. 통화 화면에서 AI 첫 인사와 TTS 재생을 확인합니다.
8. 녹음 버튼으로 사용자 발화를 전송합니다.
9. AI 응답과 음성 재생을 확인합니다.
10. 통화 종료 후 요약 화면을 확인합니다.

### 자주 발생하는 문제

#### Docker daemon 연결 실패

```text
Cannot connect to the Docker daemon
```

Docker Desktop이 꺼져 있는 상태입니다. Docker Desktop을 먼저 실행하세요.

#### DB 연결 실패

```text
P1001: Can't reach database server at localhost:5432
```

PostgreSQL 컨테이너가 실행되지 않은 상태입니다.

```bash
cd backend
docker compose up -d
```

#### 3000번 포트 충돌

```text
listen EADDRINUSE: address already in use :::3000
```

이미 3000번 포트를 사용하는 서버가 있습니다. 기존 서버를 종료하거나 백엔드 포트를 변경해야 합니다.

#### Android에서 서버 연결 실패

- Emulator: `http://10.0.2.2:3000` 사용
- 실제 기기: PC 로컬 IP 또는 ngrok URL 사용
- Android 앱에서 마이크 권한 허용 필요

### 실행 전 체크리스트

- Docker Desktop이 실행 중인지 확인
- `backend/.env`에 `OPENAI_API_KEY`가 있는지 확인
- PostgreSQL 컨테이너가 실행 중인지 확인
- 백엔드가 `npm run start`로 정상 실행되는지 확인
- Android API 주소가 테스트 환경에 맞는지 확인
- Android 앱에서 마이크 권한을 허용했는지 확인
