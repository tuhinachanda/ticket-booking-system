import api from './api';
import type { Seat, Show } from '../types';

export const showService = {
  async getShowById(showId: number): Promise<Show> {
    const response = await api.get<Show>(`/shows/${showId}`);
    return response.data;
  },

  async getShowSeats(showId: number): Promise<Seat[]> {
    const response = await api.get<Seat[]>(`/shows/${showId}/seats`);
    return response.data;
  },
};
