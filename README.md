# Pleos Connect AI Kit

Pleos Connect(현대차그룹 차량 인포테인먼트, AAOS 기반) 앱 개발용 AI 코딩 에이전트 지침 묶음. 2026-10-31에 https://document.pleos.ai 문서 141페이지를 수집해 정리.

## 사용법

프로젝트 루트에 이 폴더 내용을 그대로 복사한다.

- `AGENTS.md`: 핵심 규칙 (Codex, Cursor, Gemini CLI 등 AGENTS.md 지원 도구)
- `CLAUDE.md`: Claude Code용, AGENTS.md를 import
- `docs/`: 주제별 상세 다이제스트 5종 (출처 URL 포함)
- `raw/`: 원문 전체 텍스트 (검증/RAG용)
- `RESOURCES.md`: 전체 문서 링크 인덱스와 외부 리소스

## 주의

- SDK API 상세 레퍼런스(클래스/멤버 단위 약 1,800페이지)는 포함하지 않았다. 필요 시 RESOURCES.md의 URL 패턴으로 조회.
- 일부 수치(해상도, dp, 오디오 포커스 매트릭스)는 원문이 이미지/Figma라 텍스트로 추출되지 않았다. 각 docs 파일에 [확인 필요]로 표시.
