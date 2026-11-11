# gif생성기 안드로이드 앱

[동영상을 gif로](https://0405junha-debug.github.io/ha/) 사이트를 전체화면 웹뷰로 보여주는 앱입니다. 동영상→GIF 변환과 Giphy 검색을 그대로 사용합니다.

안드로이드 스튜디오 없이 **GitHub Actions**로 APK를 만듭니다.

## APK 받기

1. 이 폴더를 GitHub 저장소에 올립니다.
2. **Actions** 탭에서 `Build APK` 워크플로가 끝날 때까지 기다립니다.
3. 완료된 실행을 연 뒤 **Artifacts**에서 `gifmaker-apk`를 받습니다.
4. 압축을 풀면 설치 파일이 있습니다.
   - `gifmaker-release.apk` — 일반 설치용
   - `gifmaker-debug.apk` — 디버그용

직접 실행하려면 Actions 탭에서 **Run workflow**를 누르면 됩니다.

## 설치

휴대폰에서 **알 수 없는 출처** 설치를 허용한 뒤 APK를 엽니다.

## 권한

인터넷 연결과 파일 선택(동영상/GIF)만 사용합니다. 변환된 GIF는 다운로드 폴더에 저장됩니다.
