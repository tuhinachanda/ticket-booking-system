import React, { useEffect, useState } from 'react';
import type { UserBookingHistory } from '../types';
import { bookingService } from '../services/bookingService';
import { Calendar, MapPin, Ticket, Ban, RefreshCw, AlertCircle } from 'lucide-react';

interface MyBookingsProps {
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const MyBookings: React.FC<MyBookingsProps> = ({ onShowToast }) => {
  const [bookings, setBookings] = useState<UserBookingHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  useEffect(() => {
    loadBookings();
  }, []);

  const loadBookings = async () => {
    setLoading(true);
    try {
      const data = await bookingService.getMyBookings();
      setBookings(data);
    } catch (err) {
      console.error('Failed to load bookings', err);
      onShowToast('Could not load your booking history.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async (bookingId: number, showStartTime: string) => {
    const showTime = new Date(showStartTime).getTime();
    const cutoffTime = Date.now() + 2 * 60 * 60 * 1000; // 2 hours

    if (showTime <= cutoffTime) {
      onShowToast('Cannot cancel booking within 2 hours of show start time.', 'error');
      return;
    }

    if (!window.confirm('Are you sure you want to cancel this booking? Reserved seats will be freed and payment will be refunded.')) {
      return;
    }

    setCancellingId(bookingId);
    try {
      await bookingService.cancelBooking(bookingId);
      onShowToast('Booking cancelled successfully and payment refunded.', 'success');
      await loadBookings();
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to cancel booking.';
      onShowToast(msg, 'error');
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="my-bookings-container">
      <div className="my-bookings-header">
        <div>
          <h1 className="page-title">My Bookings & Reservations</h1>
          <p className="page-subtitle">Track your tickets, view payment statuses, and manage cancellations</p>
        </div>

        <button className="refresh-btn" onClick={loadBookings} title="Refresh bookings list">
          <RefreshCw size={16} className={loading ? 'spinning' : ''} />
          <span>Refresh</span>
        </button>
      </div>

      {loading ? (
        <div className="loading-state">Loading your bookings...</div>
      ) : bookings.length === 0 ? (
        <div className="empty-state-card">
          <Ticket size={48} className="empty-icon" />
          <h3>No bookings yet</h3>
          <p>Browse our upcoming events and reserve authentic seats today.</p>
        </div>
      ) : (
        <div className="bookings-list">
          {bookings.map((booking) => {
            const dateObj = new Date(booking.showStartTime);
            const isCancellable =
              booking.bookingStatus !== 'CANCELLED' &&
              booking.bookingStatus !== 'EXPIRED' &&
              dateObj.getTime() > Date.now() + 2 * 60 * 60 * 1000;

            return (
              <div key={booking.bookingId} className="booking-card">
                <div className="booking-header">
                  <div className="booking-main-info">
                    <h3 className="booking-event-title">{booking.eventTitle}</h3>
                    <div className="booking-meta-row">
                      <div className="meta-badge">
                        <MapPin size={14} />
                        <span>{booking.venueName}</span>
                      </div>
                      <div className="meta-badge">
                        <Calendar size={14} />
                        <span>
                          {dateObj.toLocaleString(undefined, {
                            weekday: 'short',
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="booking-status-badges">
                    <span className={`status-pill pill-${booking.bookingStatus.toLowerCase()}`}>
                      {booking.bookingStatus}
                    </span>
                    {booking.paymentStatus && (
                      <span className={`status-pill pill-pay-${booking.paymentStatus.toLowerCase()}`}>
                        Payment: {booking.paymentStatus}
                      </span>
                    )}
                  </div>
                </div>

                <div className="booking-body">
                  <div className="booking-seats-box">
                    <span className="seats-label">Reserved Seats:</span>
                    <div className="seats-pill-group">
                      {booking.seatLabels.map((label, idx) => (
                        <span key={idx} className="seat-chip">
                          {label}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="booking-total-box">
                    <span className="amount-label">Total Amount:</span>
                    <span className="amount-value">${Number(booking.totalAmount).toFixed(2)}</span>
                  </div>
                </div>

                <div className="booking-footer">
                  <span className="booking-date">
                    Booked on {new Date(booking.createdAt).toLocaleDateString()}
                  </span>

                  {isCancellable && (
                    <button
                      className="cancel-booking-btn"
                      onClick={() => handleCancel(booking.bookingId, booking.showStartTime)}
                      disabled={cancellingId === booking.bookingId}
                    >
                      <Ban size={16} />
                      <span>{cancellingId === booking.bookingId ? 'Cancelling...' : 'Cancel Booking'}</span>
                    </button>
                  )}

                  {!isCancellable && booking.bookingStatus === 'CONFIRMED' && (
                    <span className="cancellation-lock-notice">
                      <AlertCircle size={14} />
                      <span>Within 2 hours of showtime (non-refundable)</span>
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
