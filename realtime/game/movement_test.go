package game

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func Test_인접타일이동_허용(t *testing.T) {
	assert.True(t, CanMove(15, 10, 15, 9))  // 위
	assert.True(t, CanMove(15, 10, 15, 11)) // 아래
	assert.True(t, CanMove(15, 10, 14, 10)) // 왼쪽
	assert.True(t, CanMove(15, 10, 16, 10)) // 오른쪽
}

func Test_대각선이동_거부(t *testing.T) {
	assert.False(t, CanMove(15, 10, 16, 11))
	assert.False(t, CanMove(15, 10, 14, 9))
}

func Test_두칸이상이동_거부(t *testing.T) {
	assert.False(t, CanMove(15, 10, 17, 10))
	assert.False(t, CanMove(15, 10, 15, 8))
	assert.False(t, CanMove(0, 0, 29, 19))
}

func Test_제자리이동_거부(t *testing.T) {
	assert.False(t, CanMove(15, 10, 15, 10))
}

func Test_범위밖이동_거부(t *testing.T) {
	assert.False(t, CanMove(0, 0, -1, 0))
	assert.False(t, CanMove(0, 0, 0, -1))
	assert.False(t, CanMove(MapCols-1, 0, MapCols, 0))
	assert.False(t, CanMove(0, MapRows-1, 0, MapRows))
}

func Test_유효한방향_허용(t *testing.T) {
	for _, dir := range []string{"up", "down", "left", "right"} {
		assert.True(t, IsValidDir(dir), dir)
	}
}

func Test_유효하지않은방향_거부(t *testing.T) {
	for _, dir := range []string{"", "north", "UP", "diagonal"} {
		assert.False(t, IsValidDir(dir), dir)
	}
}

func Test_정수좌표_변환성공(t *testing.T) {
	v, ok := ParseTileCoord(15.0)

	assert.True(t, ok)
	assert.Equal(t, 15, v)
}

func Test_비정수좌표_변환실패(t *testing.T) {
	_, ok := ParseTileCoord(15.5)

	assert.False(t, ok)
}

func Test_버스트내연속이동_허용(t *testing.T) {
	now := time.Now()
	l := NewMoveLimiter(now)

	// given: 버스트 한도만큼 즉시 이동
	for i := 0; i < moveBurst; i++ {
		assert.True(t, l.Allow(now), "burst %d", i)
	}
}

func Test_버스트소진후과속이동_거부(t *testing.T) {
	now := time.Now()
	l := NewMoveLimiter(now)
	for i := 0; i < moveBurst; i++ {
		l.Allow(now)
	}

	allowed := l.Allow(now)

	assert.False(t, allowed)
}

func Test_시간경과후_토큰회복(t *testing.T) {
	now := time.Now()
	l := NewMoveLimiter(now)
	for i := 0; i < moveBurst; i++ {
		l.Allow(now)
	}
	assert.False(t, l.Allow(now))

	// when: 한 칸 이동 시간만큼 경과
	later := now.Add(moveRefillInterval)

	// then: 1회 이동 가능, 연속 2회는 불가
	assert.True(t, l.Allow(later))
	assert.False(t, l.Allow(later))
}

func Test_정상속도연속이동_항상허용(t *testing.T) {
	now := time.Now()
	l := NewMoveLimiter(now)

	// given: 클라이언트 정상 이동 주기(160ms)로 100칸 연속 이동
	for i := 0; i < 100; i++ {
		now = now.Add(160 * time.Millisecond)
		assert.True(t, l.Allow(now), "step %d", i)
	}
}
