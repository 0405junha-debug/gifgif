# 스마트 GIF 생성기 안드로이드 앱

[스마트 GIF 생성기 웹사이트](https://0405junha-debug.github.io/ha/)를 전체 화면 WebView로 여는 Android 앱입니다. 동영상→GIF 변환, Giphy 검색과 GIF 편집 기능(크기 조정, 자르기, 회전, 속도 변경 등)을 웹사이트와 함께 사용할 수 있습니다.

안드로이드 스튜디오 없이 **GitHub Actions**로 APK를 만듭니다.

## APK 받기

1. 이 폴더를 GitHub 저장소에 올립니다.
2. **Actions** 탭에서 `Build APK` 워크플로가 끝날 때까지 기다립니다.
3. 완료된 실행을 연 뒤 **Artifacts**에서 `gifmaker-apk`를 받습니다.
4. 압축을 풀면 설치 파일이 있습니다.
   - `gifmaker-release.apk` — 일반 설치용 (디버그 키로 서명)
   - `gifmaker-debug.apk` — 디버그용

직접 실행하려면 Actions 탭에서 **Run workflow**를 누르면 됩니다.

## 설치

휴대폰에서 **알 수 없는 앱 설치**를 허용한 뒤 APK를 엽니다. 이 앱은 사이트를 온라인으로 표시하므로 인터넷 연결이 필요합니다.

## 권한

인터넷 연결과 파일 선택(동영상/GIF)만 사용합니다. 변환된 GIF는 다운로드 폴더에 저장됩니다.
