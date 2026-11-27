# 🎂 인생 게임 (Roblox)

아기로 태어나서 학교에 다니고, 취직해서 돈을 벌고, 집과 차를 사다가
늙어서 생을 마감하면 **환생**해서 다시 시작하는 인생 시뮬레이션 게임입니다.

## 게임 방법

| 건물 | 행동 | 효과 |
|---|---|---|
| 🏫 학교 | 공부하기 | 학력 +2~4 (6~40살) |
| 📋 직업 센터 | 직업 찾기 | 나이·학력 조건을 맞추면 취직 |
| 🏢 회사 | 일하기 | 직업 급여만큼 돈 획득 |
| 🛒 쇼핑몰 | 쇼핑하기 | 자전거, 반려동물, 자동차, 집 등 구매 → 행복 상승 |
| 🏥 병원 | 치료받기 | 건강 +35 (20살 미만 무료) |
| 🌳 공원 쉼터 | 쉬기 | 행복 +6, 건강 +2 |

- 실제 **20초 = 게임 속 1년**. 나이가 들수록 캐릭터 크기와 걷는 속도가 바뀝니다
  (아기 → 어린이 → 청소년 → 청년 → 중년 → 노인).
- 매년 35% 확률로 랜덤 이벤트가 일어납니다. 돈 줍기, 감기, 연애, 교통사고, 복권 당첨 등이 있어요.
- 행복이 20 아래로 떨어지면 건강이 줄어듭니다. 50살부터는 해마다 건강이 조금씩 줄어요.
- 건강이 0이 되거나 수명(70~100살 중 랜덤)이 다하면 인생이 끝나요.
  **인생 점수의 10%가 다음 인생의 유산**이 됩니다.
- 진행 상황은 DataStore에 자동 저장됩니다.

## 설치 방법

### 방법 1: Rojo 사용 (추천)
1. [Rojo](https://rojo.space)를 설치합니다.
2. 이 폴더에서 `rojo serve` 를 실행합니다.
3. Roblox Studio에서 새 Baseplate를 열고 Rojo 플러그인으로 **Connect**합니다.
4. ▶ 플레이!

또는 `rojo build -o LifeGame.rbxlx` 로 바로 열 수 있는 파일을 만들 수 있어요.

### 방법 2: 직접 복사
Roblox Studio에서 아래처럼 스크립트를 만들고 파일 내용을 붙여넣으세요.

| 파일 | Studio 위치 | 종류 |
|---|---|---|
| `src/shared/Config.luau` | ReplicatedStorage › 폴더 `Shared` › `Config` | ModuleScript |
| `src/server/Main.server.luau` | ServerScriptService › 폴더 `Server` › `Main` | Script |
| `src/server/LifeService.luau` | ServerScriptService › 폴더 `Server` › `LifeService` | ModuleScript |
| `src/server/WorldBuilder.luau` | ServerScriptService › 폴더 `Server` › `WorldBuilder` | ModuleScript |
| `src/server/DataManager.luau` | ServerScriptService › 폴더 `Server` › `DataManager` | ModuleScript |
| `src/client/LifeUI.client.luau` | StarterPlayer › StarterPlayerScripts › `LifeUI` | LocalScript |

> 저장 기능을 Studio에서 테스트하려면 **게임 설정 › 보안 › Studio의 API 서비스 접근 허용**을 켜세요.

마을(건물, 길, 나무)은 서버 스크립트가 게임 시작 때 자동으로 만들기 때문에 맵을 따로 만들 필요는 없어요.

## 수정하기

`src/shared/Config.luau` 한 파일에서 대부분을 바꿀 수 있어요.
- `SECONDS_PER_YEAR`: 1년의 길이
- `Jobs`: 직업 추가/급여 조정
- `Items`: 상점 물건 추가/가격 조정
- `Places`: 건물 이름, 색, 크기

랜덤 이벤트는 `src/server/LifeService.luau`의 `EVENTS` 목록에 추가하면 됩니다.
