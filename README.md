# NPC Battle Test

**테스트 전용** 모드입니다. NPC 트레이너 둘이 실제 코블몬 배틀을 하는지 확인하려고 만들었습니다. 실제 서버에는 넣지 않아도 됩니다. (이 기능은 Poke Trainer 모드에 들어가 있습니다.)

- 버전: 0.1.0
- Minecraft 1.21.1 / Fabric
- 필요한 모드: Cobblemon, Radical Cobblemon Trainers (rctmod, rctapi), Fabric API
- 설치: 서버에만 넣으면 됩니다. 접속하는 사람(클라이언트)은 이 모드가 없어도 됩니다.

## 다운로드

**[⬇ npc-battle-test-0.1.0.jar 다운로드](https://github.com/minerapai/npc-battle-test/releases/download/v0.1.0/npc-battle-test-0.1.0.jar)**

받은 jar 파일을 마인크래프트(또는 서버) 폴더의 `mods` 폴더에 넣으면 됩니다. 다른 버전은 [Releases](https://github.com/minerapai/npc-battle-test/releases) 에 있습니다.

## 기능

- NPC 트레이너 두 명을 소환해 서로 배틀시키기

## 사용법

| 명령어 | 설명 |
|---|---|
| `/npcbattle [plain|full|random|flag]` | (OP) NPC끼리 배틀 시작 |

(OP) 표시가 있는 명령어는 서버 관리자(OP)만 쓸 수 있습니다.

## 빌드

```bash
./gradlew build
```

`build/libs/` 에 만들어진 jar 파일을 서버의 `mods` 폴더에 넣습니다.

## 라이선스

MIT License. 출처(minerapai)만 밝히면 누구나 자유롭게 쓰고 고치고 배포할 수 있습니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 보세요.
