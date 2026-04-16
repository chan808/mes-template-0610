// 백엔드 ApiResponse<T>와 1:1 대응
export interface ApiResponse<T> {
  data?: T;
  message?: string;
}
