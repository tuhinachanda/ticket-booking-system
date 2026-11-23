import api from './api';
import type { AdminStats, Event, Show, Venue } from '../types';

export const adminService = {
  async createVenue(name: string, city: string, address: string, totalRows: number, seatsPerRow: number): Promise<Venue> {
    const response = await api.post<Venue>('/admin/venues', {
      name,
      city,
      address,
      totalRows,
      seatsPerRow,
    });
    return response.data;
  },

  async createEvent(title: string, description: string, category: string, durationMinutes: number, posterUrl?: string): Promise<Event> {
    const response = await api.post<Event>('/admin/events', {
      title,
      description,
      category,
      durationMinutes,
      posterUrl,
    });
    return response.data;
  },

  async createShow(eventId: number, venueId: number, startTime: string, basePrice: number): Promise<Show> {
    const response = await api.post<Show>('/admin/shows', {
      eventId,
      venueId,
      startTime,
      basePrice,
    });
    return response.data;
  },

  async getStats(): Promise<AdminStats> {
    const response = await api.get<AdminStats>('/admin/stats');
    return response.data;
  },
};
