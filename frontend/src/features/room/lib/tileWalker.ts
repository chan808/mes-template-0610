import { Direction, MOVE_MS, TilePos } from "./tile";

// 큐 잔량에 따른 재생 배속: 2칸까지는 정속(코너가 또렷하게 보이도록), 3칸부터 가속해 따라잡는다
const MAX_SPEED_FACTOR = 3;
function speedFactor(queueLen: number): number {
  if (queueLen < 3) return 1;
  return Math.min(queueLen - 1, MAX_SPEED_FACTOR);
}

// 아바타 1개의 이동 재생기.
// 서버에서 도착하는 칸 단위 이동을 큐에 쌓고 타일당 MOVE_MS로 순서대로 재생한다.
// CSS transition 재타게팅과 달리 코너를 대각선으로 가로지르지 않고,
// 패킷이 몰려 큐가 밀리면 배속으로 따라잡는다.
export class TileWalker {
  private from: TilePos;
  private queue: TilePos[] = [];
  private progress = 0; // 현재 세그먼트 진행 시간(ms)
  private facing: Direction;

  constructor(start: TilePos, dir: Direction = "down") {
    this.from = { ...start };
    this.facing = dir;
  }

  // 마지막 목적지 기준 인접 칸이면 큐에 추가, 아니면(보정·순간이동) 즉시 스냅
  push(tile: TilePos, dir?: Direction) {
    const last = this.queue.length > 0 ? this.queue[this.queue.length - 1] : this.from;
    const dist = Math.abs(tile.x - last.x) + Math.abs(tile.y - last.y);

    if (dist === 0) {
      if (dir && !this.moving) this.facing = dir;
      return;
    }
    if (dist > 1) {
      this.snapTo(tile, dir);
      return;
    }
    this.queue.push({ ...tile });
  }

  snapTo(tile: TilePos, dir?: Direction) {
    this.from = { ...tile };
    this.queue = [];
    this.progress = 0;
    if (dir) this.facing = dir;
  }

  // dt(ms)만큼 재생을 진행한다. 세그먼트 경계의 잔여 시간은 다음 세그먼트로 이월
  update(dtMs: number) {
    let remain = dtMs;
    while (remain > 0 && this.queue.length > 0) {
      const target = this.queue[0];
      this.updateFacing(target);

      const duration = MOVE_MS / speedFactor(this.queue.length);
      const left = duration - this.progress;

      if (remain < left) {
        this.progress += remain;
        return;
      }
      remain -= left;
      this.from = this.queue.shift()!;
      this.progress = 0;
    }
  }

  private updateFacing(target: TilePos) {
    if (target.x > this.from.x) this.facing = "right";
    else if (target.x < this.from.x) this.facing = "left";
    else if (target.y > this.from.y) this.facing = "down";
    else if (target.y < this.from.y) this.facing = "up";
  }

  // 현재 위치 (타일 좌표계 실수값)
  get pos(): { x: number; y: number } {
    if (this.queue.length === 0) return { x: this.from.x, y: this.from.y };
    const target = this.queue[0];
    const duration = MOVE_MS / speedFactor(this.queue.length);
    const t = Math.min(this.progress / duration, 1);
    return {
      x: this.from.x + (target.x - this.from.x) * t,
      y: this.from.y + (target.y - this.from.y) * t,
    };
  }

  get dir(): Direction {
    return this.facing;
  }

  get moving(): boolean {
    return this.queue.length > 0;
  }
}
