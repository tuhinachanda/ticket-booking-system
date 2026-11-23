import React, { useEffect, useState } from 'react';
import type { AdminStats, Event } from '../types';
import { adminService } from '../services/adminService';
import { eventService } from '../services/eventService';
import {
  TrendingUp,
  Ticket,
  DollarSign,
  PlusCircle,
  Building,
  Film,
  Calendar,
  Percent,
  CheckCircle,
} from 'lucide-react';

interface AdminDashboardProps {
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({ onShowToast }) => {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [loadingStats, setLoadingStats] = useState(true);
  const [events, setEvents] = useState<Event[]>([]);

  // Sub-tabs
  const [activeFormTab, setActiveFormTab] = useState<'venue' | 'event' | 'show'>('venue');

  // Venue form state
  const [venueName, setVenueName] = useState('');
  const [venueCity, setVenueCity] = useState('');
  const [venueAddress, setVenueAddress] = useState('');
  const [venueRows, setVenueRows] = useState(5);
  const [venueSeatsPerRow, setVenueSeatsPerRow] = useState(10);
  const [submittingVenue, setSubmittingVenue] = useState(false);

  // Event form state
  const [eventTitle, setEventTitle] = useState('');
  const [eventDescription, setEventDescription] = useState('');
  const [eventCategory, setEventCategory] = useState('Music');
  const [eventDuration, setEventDuration] = useState(120);
  const [eventPoster, setEventPoster] = useState('');
  const [submittingEvent, setSubmittingEvent] = useState(false);

  // Show form state
  const [showEventId, setShowEventId] = useState<number | ''>('');
  const [showVenueId, setShowVenueId] = useState<number>(1);
  const [showStartTime, setShowStartTime] = useState('');
  const [showPrice, setShowPrice] = useState('45.00');
  const [submittingShow, setSubmittingShow] = useState(false);

  useEffect(() => {
    loadStats();
    loadEventsForSelect();
  }, []);

  const loadStats = async () => {
    setLoadingStats(true);
    try {
      const data = await adminService.getStats();
      setStats(data);
    } catch (err) {
      console.error('Failed to load stats', err);
      onShowToast('Could not load administrative analytics.', 'error');
    } finally {
      setLoadingStats(false);
    }
  };

  const loadEventsForSelect = async () => {
    try {
      const data = await eventService.getEvents(undefined, undefined, undefined, 0, 50);
      setEvents(data.content || []);
      if (data.content && data.content.length > 0) {
        setShowEventId(data.content[0].id);
      }
    } catch (err) {
      console.error('Failed to load events for show creation', err);
    }
  };

  const handleCreateVenue = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmittingVenue(true);
    try {
      await adminService.createVenue(venueName, venueCity, venueAddress, venueRows, venueSeatsPerRow);
      onShowToast(`Venue "${venueName}" created successfully!`, 'success');
      setVenueName('');
      setVenueCity('');
      setVenueAddress('');
      loadStats();
    } catch (err: any) {
      onShowToast(err.response?.data?.message || 'Failed to create venue.', 'error');
    } finally {
      setSubmittingVenue(false);
    }
  };

  const handleCreateEvent = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmittingEvent(true);
    try {
      const created = await adminService.createEvent(
        eventTitle,
        eventDescription,
        eventCategory,
        eventDuration,
        eventPoster || undefined
      );
      onShowToast(`Event "${created.title}" created successfully!`, 'success');
      setEventTitle('');
      setEventDescription('');
      setEventPoster('');
      loadEventsForSelect();
      loadStats();
    } catch (err: any) {
      onShowToast(err.response?.data?.message || 'Failed to create event.', 'error');
    } finally {
      setSubmittingEvent(false);
    }
  };

  const handleCreateShow = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!showEventId || !showStartTime) {
      onShowToast('Please select an event, venue, and start time.', 'error');
      return;
    }
    setSubmittingShow(true);
    try {
      const isoTime = new Date(showStartTime).toISOString();
      await adminService.createShow(Number(showEventId), Number(showVenueId), isoTime, parseFloat(showPrice));
      onShowToast('Show created and seating inventory generated!', 'success');
      loadStats();
    } catch (err: any) {
      onShowToast(err.response?.data?.message || 'Failed to create show.', 'error');
    } finally {
      setSubmittingShow(false);
    }
  };

  return (
    <div className="admin-container">
      <div className="admin-header">
        <div>
          <h1 className="page-title">Administrative Operations</h1>
          <p className="page-subtitle">Manage venues, events, schedule shows, and inspect real-time sales metrics</p>
        </div>
      </div>

      {/* KPI Cards Row */}
      {loadingStats ? (
        <div className="loading-state">Loading metrics...</div>
      ) : stats ? (
        <div className="kpi-grid">
          <div className="kpi-card">
            <div className="kpi-icon-box revenue-icon">
              <DollarSign size={24} />
            </div>
            <div className="kpi-content">
              <span className="kpi-label">Total Confirmed Revenue</span>
              <span className="kpi-value">${Number(stats.totalConfirmedRevenue).toFixed(2)}</span>
            </div>
          </div>

          <div className="kpi-card">
            <div className="kpi-icon-box bookings-icon">
              <Ticket size={24} />
            </div>
            <div className="kpi-content">
              <span className="kpi-label">Total Bookings</span>
              <span className="kpi-value">{stats.totalBookings}</span>
              <span className="kpi-subtext">
                {stats.confirmedBookings} confirmed • {stats.cancelledBookings} cancelled
              </span>
            </div>
          </div>

          <div className="kpi-card">
            <div className="kpi-icon-box occupancy-icon">
              <TrendingUp size={24} />
            </div>
            <div className="kpi-content">
              <span className="kpi-label">Active Shows Scheduled</span>
              <span className="kpi-value">{stats.showStats.length}</span>
            </div>
          </div>
        </div>
      ) : null}

      {/* Show Occupancy Analytics */}
      {stats && stats.showStats.length > 0 && (
        <div className="admin-section-card">
          <h2 className="section-title">
            <Percent size={20} />
            <span>Per-Show Occupancy & Revenue Analytics</span>
          </h2>

          <div className="occupancy-table-wrapper">
            <table className="occupancy-table">
              <thead>
                <tr>
                  <th>Event Performance</th>
                  <th>Venue</th>
                  <th>Showtime</th>
                  <th>Seats Booked</th>
                  <th>Occupancy</th>
                  <th>Revenue</th>
                </tr>
              </thead>
              <tbody>
                {stats.showStats.map((item) => (
                  <tr key={item.showId}>
                    <td>
                      <strong>{item.eventTitle}</strong>
                    </td>
                    <td>{item.venueName}</td>
                    <td>
                      {new Date(item.startTime).toLocaleString(undefined, {
                        weekday: 'short',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </td>
                    <td>
                      {item.bookedSeats} / {item.totalSeats}
                    </td>
                    <td>
                      <div className="progress-cell">
                        <div className="progress-bar-bg">
                          <div
                            className="progress-bar-fill"
                            style={{ width: `${Math.min(100, item.occupancyPercentage)}%` }}
                          />
                        </div>
                        <span className="progress-text">{item.occupancyPercentage.toFixed(1)}%</span>
                      </div>
                    </td>
                    <td>
                      <strong>${Number(item.revenue || 0).toFixed(2)}</strong>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Creation Forms Management Section */}
      <div className="admin-section-card">
        <h2 className="section-title">
          <PlusCircle size={20} />
          <span>Inventory Management</span>
        </h2>

        {/* Sub-tabs */}
        <div className="form-subtabs">
          <button
            className={`subtab-btn ${activeFormTab === 'venue' ? 'active' : ''}`}
            onClick={() => setActiveFormTab('venue')}
          >
            <Building size={16} />
            <span>Create Venue</span>
          </button>
          <button
            className={`subtab-btn ${activeFormTab === 'event' ? 'active' : ''}`}
            onClick={() => setActiveFormTab('event')}
          >
            <Film size={16} />
            <span>Create Event</span>
          </button>
          <button
            className={`subtab-btn ${activeFormTab === 'show' ? 'active' : ''}`}
            onClick={() => setActiveFormTab('show')}
          >
            <Calendar size={16} />
            <span>Schedule Show & Seats</span>
          </button>
        </div>

        {/* Venue Form */}
        {activeFormTab === 'venue' && (
          <form onSubmit={handleCreateVenue} className="admin-form">
            <div className="form-grid">
              <div className="form-group">
                <label>Venue Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Grand Apollo Hall"
                  value={venueName}
                  onChange={(e) => setVenueName(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>City *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Chicago"
                  value={venueCity}
                  onChange={(e) => setVenueCity(e.target.value)}
                />
              </div>

              <div className="form-group full-col">
                <label>Street Address *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. 120 North LaSalle Street"
                  value={venueAddress}
                  onChange={(e) => setVenueAddress(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Total Rows (Lettered A-Z) *</label>
                <input
                  type="number"
                  min="1"
                  max="26"
                  required
                  value={venueRows}
                  onChange={(e) => setVenueRows(parseInt(e.target.value) || 1)}
                />
              </div>

              <div className="form-group">
                <label>Seats Per Row *</label>
                <input
                  type="number"
                  min="1"
                  max="50"
                  required
                  value={venueSeatsPerRow}
                  onChange={(e) => setVenueSeatsPerRow(parseInt(e.target.value) || 1)}
                />
              </div>
            </div>

            <button type="submit" className="primary-btn submit-admin-btn" disabled={submittingVenue}>
              {submittingVenue ? 'Creating Venue...' : 'Save Venue'}
            </button>
          </form>
        )}

        {/* Event Form */}
        {activeFormTab === 'event' && (
          <form onSubmit={handleCreateEvent} className="admin-form">
            <div className="form-grid">
              <div className="form-group full-col">
                <label>Event Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Acoustic Guitar Night Live"
                  value={eventTitle}
                  onChange={(e) => setEventTitle(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Category *</label>
                <select value={eventCategory} onChange={(e) => setEventCategory(e.target.value)}>
                  <option value="Music">Music</option>
                  <option value="Cinema">Cinema</option>
                  <option value="Comedy">Comedy</option>
                  <option value="Theatre">Theatre</option>
                </select>
              </div>

              <div className="form-group">
                <label>Duration (Minutes) *</label>
                <input
                  type="number"
                  min="15"
                  required
                  value={eventDuration}
                  onChange={(e) => setEventDuration(parseInt(e.target.value) || 60)}
                />
              </div>

              <div className="form-group full-col">
                <label>Description</label>
                <textarea
                  rows={3}
                  placeholder="Summary of the performance, artist lineup, or plot..."
                  value={eventDescription}
                  onChange={(e) => setEventDescription(e.target.value)}
                />
              </div>
            </div>

            <button type="submit" className="primary-btn submit-admin-btn" disabled={submittingEvent}>
              {submittingEvent ? 'Creating Event...' : 'Save Event'}
            </button>
          </form>
        )}

        {/* Show Form */}
        {activeFormTab === 'show' && (
          <form onSubmit={handleCreateShow} className="admin-form">
            <div className="form-grid">
              <div className="form-group">
                <label>Select Event *</label>
                <select
                  value={showEventId}
                  onChange={(e) => setShowEventId(Number(e.target.value))}
                  required
                >
                  {events.map((ev) => (
                    <option key={ev.id} value={ev.id}>
                      {ev.title} ({ev.category})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Select Venue ID *</label>
                <select
                  value={showVenueId}
                  onChange={(e) => setShowVenueId(Number(e.target.value))}
                  required
                >
                  <option value="1">Venue 1: City Center Hall (Chicago)</option>
                  <option value="2">Venue 2: Riverside Theater (Boston)</option>
                </select>
              </div>

              <div className="form-group">
                <label>Show Start Time *</label>
                <input
                  type="datetime-local"
                  required
                  value={showStartTime}
                  onChange={(e) => setShowStartTime(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Base Seat Price ($) *</label>
                <input
                  type="number"
                  step="0.01"
                  min="1.00"
                  required
                  value={showPrice}
                  onChange={(e) => setShowPrice(e.target.value)}
                />
              </div>
            </div>

            <div className="form-note">
              <CheckCircle size={16} />
              <span>Saving a show will automatically generate all individual ShowSeat records for the venue layout.</span>
            </div>

            <button type="submit" className="primary-btn submit-admin-btn" disabled={submittingShow}>
              {submittingShow ? 'Scheduling Show...' : 'Schedule Show & Populate Seats'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
