import api from './api';
import type { BookingResponse, UserBookingHistory } from '../types';

export const bookingService = {
  async createBooking(showId: number, seatIds: number[], idempotencyKey?: string): Promise<BookingResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    const response = await api.post<BookingResponse>('/bookings', { showId, seatIds }, { headers });
    return response.data;
  },

  async getBookingById(bookingId: number): Promise<BookingResponse> {
    const response = await api.get<BookingResponse>(`/bookings/${bookingId}`);
    return response.data;
  },

  async getMyBookings(): Promise<UserBookingHistory[]> {
    const response = await api.get<UserBookingHistory[]>('/bookings/my');
    return response.data;
  },

  async cancelBooking(bookingId: number): Promise<BookingResponse> {
    const response = await api.post<BookingResponse>(`/bookings/${bookingId}/cancel`);
    return response.data;
  },
};
