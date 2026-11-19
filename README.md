# Win11 PC 에뮬레이터 (Android)

휴대폰에서 **가상 PC를 만들어 Windows 11을 설치·실행**하는 Android 앱입니다.

## 구조

```
Android 앱 (Java)
 ├─ proot (Termux 빌드, jniLibs에 포함) ── 루트 권한 없이 리눅스 실행
 │    └─ Alpine Linux 3.24 (첫 실행 시 다운로드)
 │         ├─ QEMU 11 (qemu-system-x86_64 / qemu-system-aarch64)
 │         ├─ UEFI 펌웨어 (EDK2, Secure Boot 지원)
 │         └─ swtpm (가상 TPM 2.0 — Windows 11 요구사항)
 └─ 화면: WebView + noVNC  ←─ QEMU VNC(websocket, 127.0.0.1:5700)
```

| 모드 | 가상 하드웨어 |
|---|---|
| Windows 11 x64 | q35, SMM + Secure Boot UEFI, TPM 2.0 (tpm-tis), AHCI 디스크/CD, e1000e 네트워크, USB 태블릿/키보드 |
| Windows 11 ARM64 | virt, AArch64 UEFI, TPM 2.0 (tpm-tis-device), NVMe 디스크, USB CD, USB 네트워크, ramfb 화면. `/dev/kvm` 사용 가능 시 KVM 가속 |

## 사용법

1. APK 설치 → 앱 실행 → **환경 설치** (약 200MB 다운로드, Wi‑Fi 권장)
2. **Windows 11 ISO 받기** → 앱 안에서 Microsoft 공식 다운로드 페이지가 열림 → 언어 선택 후 다운로드를 누르면
   앱이 ISO를 VM 폴더에 바로 저장 (이어받기 지원, 파일 이름으로 x64/ARM64 자동 설정).
   이미 받은 ISO가 있으면 **내 파일에서 ISO 선택**
3. 메모리(4096MB 이상)·코어·디스크(64GB 이상) 설정 후 **VM 시작**
4. 화면에서 `Press any key to boot from CD` 가 나오면 화면을 탭

ISO는 APK에 포함하지 않습니다. 5~7GB라 APK 최대 크기(4GB)를 넘고, Microsoft 라이선스상 재배포가 금지돼 있어서
매번 Microsoft 서버에서 직접 받습니다. Windows 정품 인증은 별도입니다.

## 성능에 대한 솔직한 안내

휴대폰 CPU(ARM)에는 x86 PC용 하드웨어 가상화가 없어서, QEMU가 명령어를 소프트웨어로 번역(TCG)합니다.
- **x64 Windows 11**: 동작은 하지만 매우 느립니다 (설치 수 시간, 부팅 수십 분 가능).
- **ARM64 Windows 11**: 같은 아키텍처라 훨씬 낫습니다. KVM이 열려 있는 기기라면 체감 속도가 크게 좋아집니다.

## 빌드

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

GitHub Actions(`.github/workflows/build-apk.yml`)가 push마다 APK를 빌드해 아티팩트로 올립니다.

`targetSdk 28`은 의도된 설정입니다. Android는 targetSdk 29 이상 앱이 데이터 폴더의 실행 파일을 실행하는 것을 막기 때문에 proot가 동작하지 않습니다. (Play 스토어 등록 불가, 직접 설치용)

## 문제 해결

- **VM이 갑자기 종료됨 (Android 12+)**: 팬텀 프로세스 제한. 개발자 옵션 → "하위 프로세스 제한 사용 중지", 또는
  `adb shell device_config put activity_manager max_phantom_processes 2147483647`
- **Shift+F10**: 화면을 한 번 탭한 뒤 상단 툴바의 `Shift+F10` 버튼 (QEMU에 키를 직접 눌러 보냄)
- **완전 자동 설치 (기본 켜짐)**: 빈 가상 디스크 + ISO로 VM을 시작하면 CD 부팅 키(Enter 자동 입력), 설치 언어(ISO 파일 이름으로 판단), GPT 파티션, 에디션(Pro/Home, Microsoft 공개 일반 키 — 정품 인증 아님)이 자동 처리됩니다. 이미 설치된 디스크(64MB 이상)에는 디스크 자동 선택을 적용하지 않습니다.
- **로컬 계정 자동 생성 (기본 켜짐)**: VM 시작 시 `autounattend.xml`을 담은 작은 CD를 함께 넣어, 계정·인터넷·개인정보 단계 없이 로컬 관리자 계정으로 자동 로그인합니다. 디스크·에디션 선택만 직접 하면 됩니다.
- **수동으로 MS 계정 건너뛰기**: Shift+F10 → `start ms-cxh:localonly` (예전 빌드는 `OOBE\BYPASSNRO`)
- **TPM 끄고 설치**: Shift+F10 → regedit → `HKLM\SYSTEM\Setup\LabConfig`에 `BypassTPMCheck`, `BypassSecureBootCheck`, `BypassRAMCheck` (DWORD=1)

## 사용한 오픈소스

QEMU (GPLv2), PRoot (GPLv2, Termux 패키지), talloc (LGPL), EDK2/OVMF (BSD), swtpm (BSD), Alpine Linux, noVNC (MPL 2.0, `assets/web/NOVNC-LICENSE.txt`).
