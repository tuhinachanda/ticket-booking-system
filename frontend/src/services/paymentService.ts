import api from './api';
import type { PaymentResponse } from '../types';

export const paymentService = {
  async simulatePayment(bookingId: number, simulateSuccess: boolean): Promise<PaymentResponse> {
    const response = await api.post<PaymentResponse>('/payments/simulate', {
      bookingId,
      simulateSuccess,
    });
    return response.data;
  },
};
