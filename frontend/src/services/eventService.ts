import api from './api';
import type { Event, PageResponse, Show } from '../types';

export const eventService = {
  async getEvents(city?: string, category?: string, search?: string, page: number = 0, size: number = 12): Promise<PageResponse<Event>> {
    const params: Record<string, string | number> = { page, size };
    if (city && city.trim()) params.city = city.trim();
    if (category && category.trim()) params.category = category.trim();
    if (search && search.trim()) params.search = search.trim();

    const response = await api.get<PageResponse<Event>>('/events', { params });
    return response.data;
  },

  async getEventById(id: number): Promise<Event> {
    const response = await api.get<Event>(`/events/${id}`);
    return response.data;
  },

  async getShowsForEvent(eventId: number): Promise<Show[]> {
    const response = await api.get<Show[]>(`/events/${eventId}/shows`);
    return response.data;
  },
};
