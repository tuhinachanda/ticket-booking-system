import React, { useEffect, useState } from 'react';
import type { BookingResponse, Event, Seat, Show } from '../types';
import { showService } from '../services/showService';
import { bookingService } from '../services/bookingService';
import { useAuth } from '../context/AuthContext';
import { ArrowLeft, RefreshCw, ShoppingCart, Info, Sparkles } from 'lucide-react';

interface SeatGridProps {
  show: Show;
  event: Event;
  onBack: () => void;
  onBookingCreated: (booking: BookingResponse) => void;
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const SeatGrid: React.FC<SeatGridProps> = ({
  show,
  event,
  onBack,
  onBookingCreated,
  onShowToast,
}) => {
  const { isAuthenticated, openAuthModal } = useAuth();
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [reserving, setReserving] = useState(false);
  const [conflictBanner, setConflictBanner] = useState<string | null>(null);

  useEffect(() => {
    loadSeats();
  }, [show.id]);

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

  return (
    <div className="seat-grid-container">
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
          <Info size={20} />
          <span>{conflictBanner} We refreshed the seat map for you. Please choose available seats.</span>
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
