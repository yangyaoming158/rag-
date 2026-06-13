import { http, type ApiResponse } from './http'

export interface UserDto {
  id: number
  username: string
  role: string
}

export interface LoginResponse {
  token: string
  user: UserDto
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<LoginResponse>>('/api/auth/login', { username, password })
  return response.data.data
}

export async function fetchMe() {
  const response = await http.get<ApiResponse<UserDto>>('/api/auth/me')
  return response.data.data
}
