#!/usr/bin/env bash
# 사용법: deploy.sh <이미지 태그(커밋 SHA)>
# 새 이미지로 app 컨테이너를 바꾸고 상태 확인에 실패하면 이전 태그로 되돌린다
set -euo pipefail

NEW_TAG="${1:?이미지 태그가 필요하다}"
ENV_FILE="${ENV_FILE:?ENV_FILE이 필요하다}"
STATE_FILE="${STATE_FILE:?STATE_FILE이 필요하다}"
COMPOSE_FILE="$(cd "$(dirname "$0")" && pwd)/compose.yml"
# compose.yml의 app 이미지 이름과 같아야 한다
IMAGE_REPO="ghcr.io/gsmsv/gsmsv-server-v2"

compose() {
  docker compose -p gsmsv -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

health_check() {
  local port
  port="$(compose port app 8080 | cut -d: -f2)"
  for _ in $(seq 1 30); do
    if curl -fsS "http://localhost:${port}/actuator/health" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# 이 서버 이미지 중 새 태그와 직전 태그만 남긴다. 직전 태그는 다음 배포가 실패했을 때 되돌리는 데 쓴다
cleanup_images() {
  local tag
  docker image ls "$IMAGE_REPO" --format '{{.Tag}}' | while read -r tag; do
    if [ "$tag" != "$NEW_TAG" ] && [ "$tag" != "$PREV_TAG" ]; then
      docker image rm "${IMAGE_REPO}:${tag}" || true
    fi
  done
  docker image prune -f
}

PREV_TAG="$(cat "$STATE_FILE" 2>/dev/null || true)"
echo "이전 태그: ${PREV_TAG:-없음} → 새 태그: ${NEW_TAG}"

export IMAGE_TAG="$NEW_TAG"
compose pull app
compose up -d

if health_check; then
  echo "$NEW_TAG" > "$STATE_FILE"
  # 정리에 실패해도 배포 결과는 성공으로 둔다
  cleanup_images || echo "이미지 정리 실패 (배포는 성공)"
  echo "배포 성공: ${NEW_TAG}"
  exit 0
fi

echo "상태 확인 실패: ${NEW_TAG}"
compose logs --tail 50 app || true

if [ -n "$PREV_TAG" ]; then
  echo "이전 태그로 되돌린다: ${PREV_TAG}"
  export IMAGE_TAG="$PREV_TAG"
  compose up -d app
  health_check && echo "되돌리기 성공" || echo "되돌리기도 실패. 직접 확인 필요"
fi
exit 1
