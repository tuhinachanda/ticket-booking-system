import api from './api';
import type { AuthResponse, User } from '../types';

export const authService = {
  async register(name: string, email: string, password: string): Promise<User> {
    const response = await api.post<AuthResponse>('/auth/register', { name, email, password });
    const user: User = {
      id: response.data.userId,
      name: response.data.name,
      email: response.data.email,
      role: response.data.role,
      token: response.data.token,
    };
    this.storeSession(user);
    return user;
  },

  async login(email: string, password: string): Promise<User> {
    const response = await api.post<AuthResponse>('/auth/login', { email, password });
    const user: User = {
      id: response.data.userId,
      name: response.data.name,
      email: response.data.email,
      role: response.data.role,
      token: response.data.token,
    };
    this.storeSession(user);
    return user;
  },

  logout(): void {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user_data');
  },

  storeSession(user: User): void {
    localStorage.setItem('jwt_token', user.token);
    localStorage.setItem('user_data', JSON.stringify(user));
  },

  getStoredUser(): User | null {
    try {
      const data = localStorage.getItem('user_data');
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  },
};
