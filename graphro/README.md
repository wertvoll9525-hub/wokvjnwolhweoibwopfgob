# 그래프로 (Graphro)

아무 표나 넣으면 정직한 발표용 차트가 나오는 안드로이드 앱. 서버·유료 AI·인터넷 권한 없이 기기 안에서만 동작해요.

## 폴더 구조
| 경로 | 내용 |
|---|---|
| `core/` | 순수 Kotlin 엔진: 표 읽기(붙여넣기·CSV·xlsx·JSON·문장), 단위·합계·머리글 정리, 차트 추천, 정직성 검사, 해석 문구, PPT·엑셀·CSV 생성. JVM에서 단위 테스트 |
| `app/` | Jetpack Compose 앱: 화면, 차트 렌더러(화면과 PNG가 같은 코드), 저장, 공유 |
| `store/` | 스토어 등록 문서(`listing-ko.md`), 그래픽 자산(`images/`)과 그 원본(`src/`) |
| `dist/ci/` | GitHub Actions가 올려 두는 최신 빌드 결과물 |
| `../docs/privacy.html` | 개인정보처리방침 공개 페이지 (GitHub Pages) |

## 빌드
```bash
./gradlew :core:test            # 엔진 테스트 (안드로이드 SDK 없이도 가능)
./gradlew :app:assembleDebug    # 테스트용 APK
./gradlew :app:bundleRelease    # 스토어용 AAB
```
GitHub에 푸시하면 `.github/workflows/android.yml`이 테스트와 빌드를 돌리고 결과물을 `dist/ci/`에 올려요.

## 서명
업로드 키(`graphro-upload.jks`)는 저장소에 넣지 않아요. 두 가지 방법 중 하나로 서명해요.

1. **로컬 빌드**: `graphro/signing.properties`(git에서 제외됨)에 아래 값을 적고 `./gradlew :app:bundleRelease`
   ```
   GRAPHRO_STORE_FILE=/경로/graphro-upload.jks
   GRAPHRO_STORE_PASSWORD=...
   GRAPHRO_KEY_ALIAS=graphro-upload
   GRAPHRO_KEY_PASSWORD=...
   ```
2. **GitHub Actions**: 저장소 Settings → Secrets and variables → Actions에 `GRAPHRO_KEYSTORE_BASE64`(키 파일을 base64로 인코딩한 값), `GRAPHRO_STORE_PASSWORD`, `GRAPHRO_KEY_ALIAS`, `GRAPHRO_KEY_PASSWORD`를 넣으면 CI가 서명된 AAB를 만들어요.

이미 만들어진 미서명 AAB는 이렇게 서명할 수 있어요.
```bash
jarsigner -keystore graphro-upload.jks -signedjar graphro-1.0.0.aab dist/ci/graphro-release-unsigned.aab graphro-upload
```

## 버전 올리기
`app/build.gradle.kts`의 `versionCode`(매번 +1)와 `versionName`을 바꿔요.
