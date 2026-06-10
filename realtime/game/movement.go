// Package game은 타일 기반 이동 규칙을 담당한다.
// 게임 모드(술래잡기 등) 도입 시 서버 권위 판정의 기반이 된다.
package game

import "time"

const (
	MapCols = 30
	MapRows = 20

	SpawnTileX = 15
	SpawnTileY = 10

	// 클라이언트 한 칸 이동 시간(160ms)보다 짧게 잡아 네트워크 지터를 허용
	moveRefillInterval = 140 * time.Millisecond
	// 메시지 몰림(버퍼링) 허용량 — 이를 넘는 과속 이동은 차단
	moveBurst = 3
)

// CanMove는 타일 범위와 인접성(상하좌우 1칸)을 검증한다
func CanMove(fromX, fromY, toX, toY int) bool {
	if toX < 0 || toX >= MapCols || toY < 0 || toY >= MapRows {
		return false
	}
	dx, dy := toX-fromX, toY-fromY
	if dx < 0 {
		dx = -dx
	}
	if dy < 0 {
		dy = -dy
	}
	return dx+dy == 1
}

// IsValidDir는 4방향 문자열인지 검증한다
func IsValidDir(dir string) bool {
	switch dir {
	case "up", "down", "left", "right":
		return true
	}
	return false
}

// ParseTileCoord는 정수 값인 좌표만 타일 좌표로 변환한다
func ParseTileCoord(v float64) (int, bool) {
	i := int(v)
	if float64(i) != v {
		return 0, false
	}
	return i, true
}

// MoveLimiter는 토큰 버킷으로 이동 빈도를 제한한다 (순간이동·과속 치팅 방지)
type MoveLimiter struct {
	tokens float64
	last   time.Time
}

func NewMoveLimiter(now time.Time) *MoveLimiter {
	return &MoveLimiter{tokens: moveBurst, last: now}
}

// Allow는 토큰을 회복시킨 뒤 1개를 소비한다. 토큰이 없으면 거부한다.
func (l *MoveLimiter) Allow(now time.Time) bool {
	elapsed := now.Sub(l.last)
	if elapsed > 0 {
		l.tokens += float64(elapsed) / float64(moveRefillInterval)
		if l.tokens > moveBurst {
			l.tokens = moveBurst
		}
		l.last = now
	}
	if l.tokens < 1 {
		return false
	}
	l.tokens--
	return true
}
