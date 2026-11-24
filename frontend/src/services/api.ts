import axios from 'axios';

// Read backend API URL from environment variable, falling back to local backend URL
const envUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080').trim().replace(/\/+$/, '');
const baseURL = envUrl.endsWith('/api') ? envUrl : `${envUrl}/api`;

const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Slow request / Cold-start backend tracker (triggers when request takes > 3.5s)
type SlowRequestListener = (isSlow: boolean) => void;
const slowListeners = new Set<SlowRequestListener>();

let activeRequestsCount = 0;
let slowTimer: ReturnType<typeof setTimeout> | null = null;

export const onSlowRequestChange = (listener: SlowRequestListener) => {
  slowListeners.add(listener);
  return () => {
    slowListeners.delete(listener);
  };
};

function notifySlow(isSlow: boolean) {
  slowListeners.forEach((fn) => {
    try {
      fn(isSlow);
    } catch (e) {
      console.error('Error in slow request listener', e);
    }
  });
}

function startSlowTimerIfNeeded() {
  if (activeRequestsCount > 0 && !slowTimer) {
    slowTimer = setTimeout(() => {
      if (activeRequestsCount > 0) {
        notifySlow(true);
      }
    }, 3500);
  }
}

function stopSlowTimerIfIdle() {
  if (activeRequestsCount <= 0) {
    activeRequestsCount = 0;
    if (slowTimer) {
      clearTimeout(slowTimer);
      slowTimer = null;
    }
    notifySlow(false);
  }
}

// Request interceptor: attach JWT token if present in localStorage
api.interceptors.request.use(
  (config) => {
    activeRequestsCount++;
    startSlowTimerIfNeeded();

    const token = localStorage.getItem('jwt_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    activeRequestsCount = Math.max(0, activeRequestsCount - 1);
    stopSlowTimerIfIdle();
    return Promise.reject(error);
  }
);

// Response interceptor: handle 401 unauthorized and decrement active requests
api.interceptors.response.use(
  (response) => {
    activeRequestsCount = Math.max(0, activeRequestsCount - 1);
    stopSlowTimerIfIdle();
    return response;
  },
  (error) => {
    activeRequestsCount = Math.max(0, activeRequestsCount - 1);
    stopSlowTimerIfIdle();

    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_data');
    }
    return Promise.reject(error);
  }
);

export default api;
