export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Room {
  id: number;
  name: string;
  ownerId: number;
  isPrivate: boolean;
  maxCapacity: number;
  status: "active" | "closed";
  inviteToken: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RoomSummary {
  id: number;
  name: string;
  ownerId: number;
  isPrivate: boolean;
  maxCapacity: number;
  status: "active" | "closed";
  createdAt: string;
}

export interface CreateRoomRequest {
  name: string;
  maxCapacity: number;
  isPrivate: boolean;
}

export interface UpdateRoomRequest {
  name?: string;
  maxCapacity?: number;
  isPrivate?: boolean;
}

// GET /api/v1/rooms/join/{token} 응답 타입
export interface RoomJoinInfo {
  id: number;
  name: string;
  isPrivate: boolean;
  maxCapacity: number;
  status: "active" | "closed";
}
