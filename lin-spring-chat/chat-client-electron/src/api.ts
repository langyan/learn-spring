import axios from 'axios';
import type {
  AuthResponse,
  ConversationView,
  MessageDto,
  ReadStatusView,
  UserItem,
} from './types';

export const API_BASE = 'http://localhost:8082';

export const api = axios.create({ baseURL: API_BASE, timeout: 10000 });

let token: string | null = localStorage.getItem('chat.token');

export function setToken(value: string | null) {
  token = value;
  if (value) {
    localStorage.setItem('chat.token', value);
  } else {
    localStorage.removeItem('chat.token');
  }
}

api.interceptors.request.use((config) => {
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 统一错误提取
export function errMsg(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { error?: string } | undefined;
    return data?.error ?? e.message;
  }
  return e instanceof Error ? e.message : String(e);
}

// ---------- Auth ----------
export const authApi = {
  register: (body: { username: string; password: string; nickname?: string }) =>
    api.post<AuthResponse>('/api/auth/register', body).then((r) => r.data),
  login: (body: { username: string; password: string }) =>
    api.post<AuthResponse>('/api/auth/login', body).then((r) => r.data),
};

// ---------- Users ----------
export const userApi = {
  search: (q: string) =>
    api.get<UserItem[]>('/api/users/search', { params: { q } }).then((r) => r.data),
};

// ---------- Conversations ----------
export const conversationApi = {
  list: () => api.get<ConversationView[]>('/api/conversations').then((r) => r.data),
  detail: (id: string) => api.get<ConversationView>(`/api/conversations/${id}`).then((r) => r.data),
  direct: (targetUserId: string) =>
    api.post<ConversationView>('/api/conversations/direct', { targetUserId }).then((r) => r.data),
  createGroup: (name: string, memberIds: string[]) =>
    api.post<ConversationView>('/api/conversations/group', { name, memberIds }).then((r) => r.data),
  rename: (id: string, name: string) => api.patch(`/api/conversations/${id}`, { name }),
  invite: (id: string, userIds: string[]) =>
    api.post<ConversationView>(`/api/conversations/${id}/members`, { userIds }).then((r) => r.data),
  removeMember: (id: string, userId: string) =>
    api.delete(`/api/conversations/${id}/members/${userId}`),
  leave: (id: string) => api.post(`/api/conversations/${id}/leave`),
  dissolve: (id: string) => api.delete(`/api/conversations/${id}`),
};

// ---------- Messages ----------
export const messageApi = {
  send: (conversationId: string, content: string) =>
    api
      .post<MessageDto>(`/api/conversations/${conversationId}/messages`, { content })
      .then((r) => r.data),
  history: (conversationId: string, before?: number, beforeId?: string, size = 30) =>
    api
      .get<MessageDto[]>(`/api/conversations/${conversationId}/messages`, {
        params: { before, beforeId, size },
      })
      .then((r) => r.data),
  markRead: (conversationId: string, lastReadMessageId: string) =>
    api.post(`/api/conversations/${conversationId}/read`, { lastReadMessageId }),
  readStatus: (conversationId: string, messageId: string) =>
    api
      .get<ReadStatusView>(`/api/conversations/${conversationId}/messages/${messageId}/read-status`)
      .then((r) => r.data),
  recall: (messageId: string) => api.post(`/api/messages/${messageId}/recall`),
  undelivered: () => api.get<MessageDto[]>('/api/messages/undelivered').then((r) => r.data),
};
