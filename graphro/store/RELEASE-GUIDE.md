# 그래프로 출시 순서

## 준비물
| 항목 | 위치 |
|---|---|
| 서명된 AAB | `graphro/dist/release/graphro-1.1.0.aab`, `-1.1.1.aab`, `-1.2.0.aab` (업데이트 순서대로, 출시 노트는 `store/release-notes.md`) |
| 업로드 키 | `graphro-upload.jks` + `업로드키_정보.txt` (대화에서 따로 전달, 저장소에는 없음) |
| 등록 문구 | `graphro/store/listing-ko.md`, `listing-en.md`, `listing-ja.md` |
| 이미지 | `graphro/store/images/icon-512.png` + 언어별 `images/ko`, `images/en`, `images/ja` (그래픽 이미지, 스크린샷 6장) |
| 개인정보처리방침 | `docs/privacy.html`(한국어), `privacy-en.html`, `privacy-ja.html` → 공개 URL 필요 (아래 2번) |
| 테스트용 APK | `graphro/dist/ci/graphro-debug.apk` (폰에 바로 설치해 볼 수 있어요) |

## 1. 업로드 키 보관 (가장 중요)
- `graphro-upload.jks`와 비밀번호를 안전한 곳 두 군데 이상(예: 비밀번호 관리자 + 외장 저장소)에 보관하세요.
- Play 앱 서명(기본값)을 쓰면 실제 배포 서명 키는 Google이 보관해요. 업로드 키를 잃어버려도 Play Console에서 재설정을 요청할 수 있지만 며칠이 걸려요.

## 2. 개인정보처리방침 URL 열기
GitHub Pages로 무료 공개할 수 있어요.
1. 이 브랜치를 `main`에 병합해요 (또는 Pages에서 이 브랜치를 선택).
2. 저장소 Settings → Pages → Build and deployment → Source: *Deploy from a branch* → Branch: `main`, 폴더: `/docs` → Save.
3. 몇 분 뒤 `https://wertvoll9525-hub.github.io/wokvjnwolhweoibwopfgob/privacy.html`이 열려요.
- 저장소가 비공개면 무료 플랜에서는 Pages를 쓸 수 없어요. 그때는 저장소를 공개로 바꾸거나, `privacy.html` 내용을 Google Sites·노션 공개 페이지에 붙여넣어 그 주소를 쓰세요.

## 3. Play Console 앱 만들기
1. https://play.google.com/console 에서 개발자 계정 등록 (등록비 25달러, 본인 인증 필요).
2. **앱 만들기** → 앱 이름 `그래프로 - 틀리지 않는 차트`, 기본 언어 한국어, 앱, 무료.
3. 대시보드의 **앱 설정** 항목을 `listing-ko.md` 그대로 채워요.
   - 개인정보처리방침 URL
   - 앱 액세스 권한: 제한 없음
   - 광고: 없음
   - 콘텐츠 등급: 설문 답변은 문서 참고 → 전체이용가 예상
   - 타겟층: 18세 이상
   - 데이터 보안: 수집·공유 없음
   - 카테고리: 생산성, 연락처 이메일: wertvoll9525@gmail.com
4. **기본 스토어 등록정보**: 간단한 설명, 자세한 설명, 아이콘(512), 그래픽 이미지(1024×500), 휴대전화 스크린샷 6장.

### 영어·일본어 등록정보 추가
1. Play Console → 성장 → 스토어 등록정보 → 기본 스토어 등록정보 → **번역 관리** → **자체 번역 추가**.
2. 영어(미국) en-US, 일본어 ja-JP를 추가해요.
3. 각 언어에 `listing-en.md` / `listing-ja.md`의 앱 이름·간단한 설명·자세한 설명을 붙여넣어요.
4. 언어별 그래픽: 그래픽 이미지와 스크린샷 6장을 `images/en`, `images/ja`에서 올려요. 아이콘은 공통이에요.
5. 출시 노트도 언어별로 넣을 수 있어요(각 문서의 ‘Release notes’).
- 앱 안의 언어는 폰 언어를 따라가요. 한국어·영어·일본어 외의 언어에서는 영어로 보여요.

## 4. 테스트 → 정식 출시
2023년 11월 이후 만든 **개인 개발자 계정은 바로 정식 출시할 수 없어요.**
1. **비공개 테스트** 트랙을 만들고 AAB(`graphro-1.1.0.aab`)를 올려요. 출시 노트는 `listing-ko.md`에 있어요.
2. 테스터를 **12명 이상** 등록하고(이메일 목록 또는 Google 그룹), 테스터들이 **14일 연속** 참여하도록 해요.
3. 14일이 지나면 대시보드에서 **프로덕션 액세스 신청**을 해요(테스트 소감 몇 가지 질문에 답).
4. 승인되면 프로덕션 트랙에 같은 AAB를 올려 출시해요. 심사는 보통 며칠 걸려요.
- 조직(사업자) 계정은 이 테스트 요건이 없어요.

## 5. 업데이트할 때
1. `graphro/app/build.gradle.kts`에서 `versionCode`를 1 올리고 `versionName`을 바꿔요.
2. 푸시하면 GitHub Actions가 테스트·빌드·에뮬레이터 검사를 해요. 결과는 `graphro/dist/ci/`.
3. 서명: `graphro/README.md`의 ‘서명’ 참고. GitHub Secrets에 키를 넣어두면 CI가 서명된 AAB를 바로 만들어요.

## 알아둘 점
- 스토어 스크린샷은 에뮬레이터에서 실제 앱을 실행해 찍은 화면에 문구를 얹은 이미지예요.
- `.xls`(엑셀 97-2003)와 표 사진 인식은 아직 지원하지 않아요. 앱이 `.xlsx`로 저장해 달라고 안내해요.
- 인터넷 권한이 없어서 크래시 보고는 Play Console의 ‘Android vitals’로 확인해요.
