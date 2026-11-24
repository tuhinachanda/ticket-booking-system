import React, { useEffect, useState } from 'react';
import type { BookingResponse, Event, Seat, Show } from '../types';
import { showService } from '../services/showService';
import { bookingService } from '../services/bookingService';
import { useAuth } from '../context/AuthContext';
import { ArrowLeft, ArrowRight, Clock, RefreshCw, ShoppingCart, Sparkles, AlertTriangle } from 'lucide-react';

interface SeatGridProps {
  show: Show;
  event: Event;
  activeBooking: BookingResponse | null;
  onBack: () => void;
  onBookingCreated: (booking: BookingResponse) => void;
  onOpenPayment: () => void;
  onHoldExpired: () => void;
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const SeatGrid: React.FC<SeatGridProps> = ({
  show,
  event,
  activeBooking,
  onBack,
  onBookingCreated,
  onOpenPayment,
  onHoldExpired,
  onShowToast,
}) => {
  const { isAuthenticated, openAuthModal } = useAuth();
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [reserving, setReserving] = useState(false);
  const [conflictBanner, setConflictBanner] = useState<string | null>(null);

  // Live hold countdown timer for active booking
  const [holdSecondsLeft, setHoldSecondsLeft] = useState<number>(() => {
    if (!activeBooking?.holdExpiresAt) return 0;
    const diff = Math.floor((new Date(activeBooking.holdExpiresAt).getTime() - Date.now()) / 1000);
    return Math.max(0, diff);
  });

  useEffect(() => {
    loadSeats();
  }, [show.id]);

  // Keep hold timer synced with activeBooking
  useEffect(() => {
    if (!activeBooking?.holdExpiresAt || activeBooking.status !== 'PENDING') {
      setHoldSecondsLeft(0);
      return;
    }

    const calcDiff = () => {
      const diff = Math.floor((new Date(activeBooking.holdExpiresAt!).getTime() - Date.now()) / 1000);
      return Math.max(0, diff);
    };

    setHoldSecondsLeft(calcDiff());

    const interval = setInterval(() => {
      const remaining = calcDiff();
      setHoldSecondsLeft(remaining);

      if (remaining <= 0) {
        clearInterval(interval);
        onHoldExpired();
        onShowToast('Your seat hold lease has expired. Seats were released back to public availability.', 'error');
        loadSeats();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [activeBooking]);

  const loadSeats = async () => {
    setLoading(true);
    try {
      const data = await showService.getShowSeats(show.id);
      setSeats(data);
    } catch (err) {
      console.error('Failed to load seats', err);
      onShowToast('Could not load seat map. Please try again.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleSeatClick = (seat: Seat) => {
    // Only available seats can be selected
    if (seat.effectiveStatus !== 'AVAILABLE') return;

    setConflictBanner(null);
    setSelectedSeatIds((prev) => {
      const next = new Set(prev);
      if (next.has(seat.id)) {
        next.delete(seat.id);
      } else {
        next.add(seat.id);
      }
      return next;
    });
  };

  // Group seats by row
  const rowMap = seats.reduce((acc, seat) => {
    if (!acc[seat.seatRow]) {
      acc[seat.seatRow] = [];
    }
    acc[seat.seatRow].push(seat);
    return acc;
  }, {} as Record<string, Seat[]>);

  const sortedRows = Object.keys(rowMap).sort();

  // Selected seats information
  const selectedSeatsList = seats.filter((s) => selectedSeatIds.has(s.id));
  const totalPrice = selectedSeatsList.reduce((sum, s) => sum + Number(s.price), 0);

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleReserveAndPay = async () => {
    if (!isAuthenticated) {
      onShowToast('Please sign in or use demo accounts to reserve seats.', 'info');
      openAuthModal();
      return;
    }

    if (selectedSeatIds.size === 0) {
      onShowToast('Please select at least one seat.', 'info');
      return;
    }

    setReserving(true);
    setConflictBanner(null);

    try {
      const seatIdsArray = Array.from(selectedSeatIds);
      const booking = await bookingService.createBooking(show.id, seatIdsArray);
      onShowToast('Seats reserved successfully! Complete payment before your 5-minute lease expires.', 'success');
      onBookingCreated(booking);
      setSelectedSeatIds(new Set());
    } catch (err: any) {
      if (err.response?.status === 409) {
        // Double-booking conflict rule:
        const msg = err.response?.data?.message || 'One or more seats were just taken by another user!';
        setConflictBanner(msg);
        onShowToast(msg, 'error');
        // Clear selection & automatically refresh seat grid
        setSelectedSeatIds(new Set());
        await loadSeats();
      } else {
        const errorMsg = err.response?.data?.message || 'Reservation failed. Please try again.';
        onShowToast(errorMsg, 'error');
      }
    } finally {
      setReserving(false);
    }
  };

  const formattedStartTime = new Date(show.startTime).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  const hasActiveHoldForThisShow =
    activeBooking &&
    activeBooking.showId === show.id &&
    activeBooking.status === 'PENDING' &&
    holdSecondsLeft > 0;

  return (
    <div className="seat-grid-container">
      {/* Active Hold Countdown Banner */}
      {hasActiveHoldForThisShow && (
        <div className="active-hold-bar">
          <div className="active-hold-info">
            <Clock size={20} className="hold-clock-pulse" />
            <div>
              <span className="hold-bar-title">Active Seat Hold in Progress</span>
              <p className="hold-bar-desc">
                Seats: <strong>{activeBooking.seats.map((s) => `${s.seatRow}${s.seatNumber}`).join(', ')}</strong> • Total: <strong>${Number(activeBooking.totalAmount).toFixed(2)}</strong>
              </p>
            </div>
          </div>
          <div className="active-hold-actions">
            <div className="hold-countdown-pill">
              <span className="pill-label">Expires in:</span>
              <span className="pill-digits">{formatTimer(holdSecondsLeft)}</span>
            </div>
            <button className="primary-btn complete-hold-btn" onClick={onOpenPayment}>
              <span>Complete Payment</span>
              <ArrowRight size={16} />
            </button>
          </div>
        </div>
      )}

      {/* Top Header */}
      <div className="seat-grid-header">
        <button className="back-btn" onClick={onBack}>
          <ArrowLeft size={18} />
          <span>Back to Shows</span>
        </button>

        <div className="header-show-details">
          <h2>{event.title}</h2>
          <p className="show-subtitle">
            {show.venue.name} ({show.venue.city}) • {formattedStartTime}
          </p>
        </div>

        <button className="refresh-btn" onClick={loadSeats} title="Refresh seat availability">
          <RefreshCw size={16} className={loading ? 'spinning' : ''} />
          <span>Refresh</span>
        </button>
      </div>

      {/* 409 Conflict Banner */}
      {conflictBanner && (
        <div className="conflict-alert-banner">
          <AlertTriangle size={20} />
          <span>{conflictBanner} We refreshed the seat map for you. Please choose from available seats.</span>
        </div>
      )}

      {/* Legend */}
      <div className="seat-legend">
        <div className="legend-item">
          <span className="legend-box seat-available" />
          <span>Available</span>
        </div>
        <div className="legend-item">
          <span className="legend-box seat-selected" />
          <span>Selected</span>
        </div>
        <div className="legend-item">
          <span className="legend-box seat-held" />
          <span>Held (In Cart)</span>
        </div>
        <div className="legend-item">
          <span className="legend-box seat-booked" />
          <span>Booked</span>
        </div>
      </div>

      {/* Cinema / Stage Screen Visual Curve */}
      <div className="screen-visual-wrapper">
        <div className="screen-curve" />
        <span className="screen-label">STAGE / SCREEN</span>
      </div>

      {/* Interactive Seat Rows */}
      {loading ? (
        <div className="loading-state">Loading seating map...</div>
      ) : (
        <div className="seat-map-layout">
          {sortedRows.map((rowLabel) => {
            const rowSeats = rowMap[rowLabel].sort((a, b) => a.seatNumber - b.seatNumber);
            return (
              <div key={rowLabel} className="seat-row">
                <span className="row-letter">{rowLabel}</span>
                <div className="row-seats">
                  {rowSeats.map((seat) => {
                    const isSelected = selectedSeatIds.has(seat.id);
                    let statusClass = 'seat-available';

                    if (seat.effectiveStatus === 'BOOKED') {
                      statusClass = 'seat-booked';
                    } else if (seat.effectiveStatus === 'HELD') {
                      statusClass = 'seat-held';
                    } else if (isSelected) {
                      statusClass = 'seat-selected';
                    }

                    return (
                      <button
                        key={seat.id}
                        type="button"
                        className={`seat-unit ${statusClass}`}
                        disabled={seat.effectiveStatus === 'BOOKED' || seat.effectiveStatus === 'HELD'}
                        onClick={() => handleSeatClick(seat)}
                        title={`Seat ${seat.seatRow}${seat.seatNumber} - $${Number(seat.price).toFixed(2)} (${seat.effectiveStatus})`}
                      >
                        <span className="seat-num">{seat.seatNumber}</span>
                      </button>
                    );
                  })}
                </div>
                <span className="row-letter">{rowLabel}</span>
              </div>
            );
          })}
        </div>
      )}

      {/* Live Booking Summary Bottom Bar */}
      {selectedSeatIds.size > 0 && (
        <div className="booking-summary-bar">
          <div className="summary-left">
            <div className="selected-tag">
              <Sparkles size={16} />
              <span>
                {selectedSeatsList.length} {selectedSeatsList.length === 1 ? 'Seat' : 'Seats'} Selected
              </span>
            </div>
            <div className="seats-list-tags">
              {selectedSeatsList.map((s) => (
                <span key={s.id} className="seat-pill">
                  {s.seatRow}{s.seatNumber}
                </span>
              ))}
            </div>
          </div>

          <div className="summary-right">
            <div className="total-price-box">
              <span className="total-label">Total Price:</span>
              <span className="total-amount">${totalPrice.toFixed(2)}</span>
            </div>

            <button
              className="primary-btn reserve-pay-btn"
              onClick={handleReserveAndPay}
              disabled={reserving}
            >
              <ShoppingCart size={18} />
              <span>{reserving ? 'Reserving Seats...' : 'Reserve & Pay'}</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
