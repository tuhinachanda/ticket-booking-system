import React, { useEffect, useState } from 'react';
import type { Event, Show } from '../types';
import { eventService } from '../services/eventService';
import { Search, MapPin, Calendar, Clock, ChevronRight, Tag } from 'lucide-react';

interface EventDiscoveryProps {
  onSelectShow: (show: Show, event: Event) => void;
}

export const EventDiscovery: React.FC<EventDiscoveryProps> = ({ onSelectShow }) => {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [city, setCity] = useState('');
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
  const [shows, setShows] = useState<Show[]>([]);
  const [loadingShows, setLoadingShows] = useState(false);

  const availableCities = ['All Cities', 'Chicago', 'Boston', 'New York', 'San Francisco'];

  useEffect(() => {
    loadEvents();
  }, [city]);

  const loadEvents = async () => {
    setLoading(true);
    try {
      const cityFilter = city && city !== 'All Cities' ? city : undefined;
      const data = await eventService.getEvents(cityFilter, undefined, search || undefined);
      setEvents(data.content || []);
    } catch (err) {
      console.error('Failed to load events', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadEvents();
  };

  const handleEventClick = async (event: Event) => {
    setSelectedEvent(event);
    setLoadingShows(true);
    try {
      const showList = await eventService.getShowsForEvent(event.id);
      setShows(showList);
    } catch (err) {
      console.error('Failed to load shows for event', err);
    } finally {
      setLoadingShows(false);
    }
  };

  return (
    <div className="discovery-container">
      {/* Hero Banner & Search Filter */}
      <div className="hero-section">
        <h1 className="hero-title">Experience Live Entertainment</h1>
        <p className="hero-subtitle">
          Book authentic seats for concerts, indie cinema screenings, and live comedy showcases with zero double-booking risk.
        </p>

        <form onSubmit={handleSearchSubmit} className="search-bar-card">
          <div className="search-input-group">
            <Search size={20} className="search-icon" />
            <input
              type="text"
              placeholder="Search events, shows, performances..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="city-filter-group">
            <MapPin size={20} className="city-icon" />
            <select value={city} onChange={(e) => setCity(e.target.value)}>
              {availableCities.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>

          <button type="submit" className="primary-btn search-btn">
            Find Shows
          </button>
        </form>
      </div>

      {/* Main Events Grid */}
      <div className="events-content-layout">
        <div className="events-grid-wrapper">
          <h2 className="section-title">
            Featured Performances
            <span className="badge-count">{events.length}</span>
          </h2>

          {loading ? (
            <div className="loading-state">Loading performances...</div>
          ) : events.length === 0 ? (
            <div className="empty-state">No events found matching your criteria. Try adjusting your filters.</div>
          ) : (
            <div className="events-grid">
              {events.map((event) => (
                <div
                  key={event.id}
                  className={`event-card ${selectedEvent?.id === event.id ? 'selected-card' : ''}`}
                  onClick={() => handleEventClick(event)}
                >
                  <div className="event-badge">
                    <Tag size={12} />
                    <span>{event.category}</span>
                  </div>

                  <div className="event-body">
                    <h3 className="event-title">{event.title}</h3>
                    <p className="event-desc">{event.description}</p>

                    <div className="event-meta">
                      <div className="meta-item">
                        <Clock size={14} />
                        <span>{event.durationMinutes} mins</span>
                      </div>
                    </div>
                  </div>

                  <div className="event-footer">
                    <span>View Showtimes</span>
                    <ChevronRight size={18} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Showtimes Slide-out / Side Panel */}
        {selectedEvent && (
          <aside className="shows-sidebar">
            <div className="shows-sidebar-header">
              <h3>Upcoming Showtimes</h3>
              <p className="sidebar-event-title">{selectedEvent.title}</p>
            </div>

            {loadingShows ? (
              <div className="loading-state">Fetching showtimes & availability...</div>
            ) : shows.length === 0 ? (
              <div className="empty-state">No upcoming showtimes scheduled for this event yet.</div>
            ) : (
              <div className="shows-list">
                {shows.map((show) => {
                  const dateObj = new Date(show.startTime);
                  const formattedDate = dateObj.toLocaleDateString(undefined, {
                    weekday: 'short',
                    month: 'short',
                    day: 'numeric',
                  });
                  const formattedTime = dateObj.toLocaleTimeString(undefined, {
                    hour: '2-digit',
                    minute: '2-digit',
                  });

                  return (
                    <div
                      key={show.id}
                      className="show-card"
                      onClick={() => onSelectShow(show, selectedEvent)}
                    >
                      <div className="show-datetime">
                        <div className="show-date">
                          <Calendar size={14} />
                          <span>{formattedDate}</span>
                        </div>
                        <div className="show-time">{formattedTime}</div>
                      </div>

                      <div className="show-venue">
                        <strong>{show.venue.name}</strong>
                        <span>{show.venue.city} • {show.venue.address}</span>
                      </div>

                      <div className="show-pricing-availability">
                        <div className="show-seats-avail">
                          <span className="avail-number">{show.availableSeatsCount}</span>
                          <span className="avail-label">seats left</span>
                        </div>
                        <div className="show-price">
                          <span className="from-label">from</span>
                          <span className="price-tag">${Number(show.basePrice).toFixed(2)}</span>
                        </div>
                      </div>

                      <button className="select-seats-btn">
                        <span>Select Seats</span>
                        <ChevronRight size={16} />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </aside>
        )}
      </div>
    </div>
  );
};
