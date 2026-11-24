import React, { useEffect, useState } from 'react';
import type { BookingResponse, PaymentResponse } from '../types';
import { paymentService } from '../services/paymentService';
import {
  Clock,
  CheckCircle2,
  XCircle,
  ShieldCheck,
  AlertTriangle,
  ArrowRight,
  X,
  RotateCcw,
} from 'lucide-react';

interface PaymentModalProps {
  booking: BookingResponse;
  onClose: () => void;
  onPaymentComplete: () => void;
  onHoldExpired?: () => void;
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const PaymentModal: React.FC<PaymentModalProps> = ({
  booking,
  onClose,
  onPaymentComplete,
  onHoldExpired,
  onShowToast,
}) => {
  const [secondsLeft, setSecondsLeft] = useState<number>(() => {
    if (!booking.holdExpiresAt) return 300;
    const diff = Math.floor((new Date(booking.holdExpiresAt).getTime() - Date.now()) / 1000);
    return Math.max(0, diff);
  });

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PaymentResponse | null>(null);

  // Live countdown timer
  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          if (onHoldExpired) {
            onHoldExpired();
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleSimulatePayment = async (success: boolean) => {
    if (secondsLeft === 0) {
      onShowToast('Hold lease has expired! Please select seats again.', 'error');
      return;
    }

    setLoading(true);
    try {
      const res = await paymentService.simulatePayment(booking.id, success);
      setResult(res);

      if (success) {
        // Persist confirmation reference in sessionStorage so user views highlight it
        try {
          sessionStorage.setItem('last_confirmed_booking_id', String(booking.id));
        } catch (_) {}
        onShowToast('Payment succeeded! Your booking is confirmed.', 'success');
      } else {
        onShowToast('Payment failed. Reserved seats have been released.', 'error');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Payment processing failed.';
      onShowToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  const formattedStartTime = new Date(booking.showStartTime).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className="modal-overlay">
      <div className="modal-content payment-modal">
        <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
          <X size={20} />
        </button>

        {!result ? (
          <>
            <div className="payment-header">
              <div className="payment-badge">
                <ShieldCheck size={18} />
                <span>Secure Checkout Simulation</span>
              </div>
              <h2 className="modal-title">Complete Your Reservation</h2>
              <p className="modal-subtitle">
                {booking.showTitle} at {booking.venueName}
              </p>
            </div>

            {/* Hold Expiration Countdown Timer */}
            <div className={`countdown-box ${secondsLeft < 60 ? 'countdown-urgent' : ''}`}>
              <div className="countdown-icon">
                <Clock size={22} className={secondsLeft < 60 ? 'pulse-urgent' : ''} />
              </div>
              <div className="countdown-text">
                <span className="countdown-label">Seat Hold Lease Countdown:</span>
                <span className="countdown-digits">{formatTimer(secondsLeft)}</span>
              </div>
              {secondsLeft === 0 && (
                <div className="expired-notice">
                  <AlertTriangle size={16} />
                  <span>Hold expired. Seats released back to the public pool.</span>
                </div>
              )}
            </div>

            {/* Order Details */}
            <div className="order-summary-card">
              <div className="order-row">
                <span className="order-label">Booking Reference:</span>
                <span className="order-value order-ref">#BK-{booking.id}</span>
              </div>
              <div className="order-row">
                <span className="order-label">Selected Seats:</span>
                <span className="order-value seats-highlight">
                  {booking.seats.map((s) => `${s.seatRow}${s.seatNumber}`).join(', ')} ({booking.seats.length} {booking.seats.length === 1 ? 'seat' : 'seats'})
                </span>
              </div>
              <div className="order-row">
                <span className="order-label">Showtime:</span>
                <span className="order-value">{formattedStartTime}</span>
              </div>
              <div className="order-divider" />
              <div className="order-row total-row">
                <span className="total-title">Total Due:</span>
                <span className="total-price">${Number(booking.totalAmount).toFixed(2)}</span>
              </div>
            </div>

            {/* Simulation Actions */}
            {secondsLeft > 0 ? (
              <div className="simulation-actions">
                <button
                  className="simulate-btn success-btn"
                  disabled={loading}
                  onClick={() => handleSimulatePayment(true)}
                >
                  <CheckCircle2 size={18} />
                  <span>{loading ? 'Processing...' : 'Simulate Payment Success'}</span>
                </button>

                <button
                  className="simulate-btn failure-btn"
                  disabled={loading}
                  onClick={() => handleSimulatePayment(false)}
                >
                  <XCircle size={18} />
                  <span>{loading ? 'Processing...' : 'Simulate Payment Failure'}</span>
                </button>
              </div>
            ) : (
              <div className="expired-action-container">
                <button className="primary-btn finish-btn" onClick={onClose}>
                  <RotateCcw size={18} />
                  <span>Release & Select New Seats</span>
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="payment-result-box">
            {result.paymentStatus === 'SUCCEEDED' ? (
              <div className="result-icon success-icon">
                <CheckCircle2 size={48} />
              </div>
            ) : (
              <div className="result-icon failure-icon">
                <XCircle size={48} />
              </div>
            )}

            <h3 className="result-title">
              {result.paymentStatus === 'SUCCEEDED' ? 'Booking Confirmed!' : 'Payment Simulation Failed'}
            </h3>

            <p className="result-desc">
              {result.paymentStatus === 'SUCCEEDED'
                ? 'Your seats have been booked permanently and recorded in the database. You can review ticket details and cancellation options anytime in My Bookings.'
                : 'The simulation recorded a failed payment. Your held seats have been safely released back to available status.'}
            </p>

            {/* Complete Booking Confirmation Summary Card */}
            {result.paymentStatus === 'SUCCEEDED' ? (
              <div className="confirmation-summary-card">
                <div className="confirmation-header-strip">
                  <span className="confirmation-badge">Official Booking Confirmation</span>
                  <span className="confirmation-ref">#BK-{booking.id}</span>
                </div>

                <div className="confirmation-grid">
                  <div className="conf-item">
                    <span className="conf-label">Event & Show</span>
                    <strong className="conf-value">{booking.showTitle}</strong>
                  </div>
                  <div className="conf-item">
                    <span className="conf-label">Venue</span>
                    <span className="conf-value">{booking.venueName}</span>
                  </div>
                  <div className="conf-item">
                    <span className="conf-label">Date & Showtime</span>
                    <span className="conf-value">{formattedStartTime}</span>
                  </div>
                  <div className="conf-item">
                    <span className="conf-label">Confirmed Seats</span>
                    <div className="conf-seat-chips">
                      {booking.seats.map((s) => (
                        <span key={s.showSeatId} className="conf-chip">
                          {s.seatRow}{s.seatNumber}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="conf-item">
                    <span className="conf-label">Total Amount Paid</span>
                    <strong className="conf-price">${Number(result.amount).toFixed(2)}</strong>
                  </div>
                  <div className="conf-item">
                    <span className="conf-label">Transaction Reference</span>
                    <code className="conf-tx">{result.transactionRef}</code>
                  </div>
                </div>

                <div className="conf-cancellation-note">
                  <ShieldCheck size={16} />
                  <span>
                    <strong>Cancellation Policy:</strong> Free cancellation with full refund is available in My Bookings up to 2 hours before showtime.
                  </span>
                </div>
              </div>
            ) : (
              <div className="receipt-box">
                <div className="receipt-row">
                  <span>Transaction Ref:</span>
                  <code>{result.transactionRef}</code>
                </div>
                <div className="receipt-row">
                  <span>Status:</span>
                  <span className="status-pill pill-cancelled">FAILED</span>
                </div>
              </div>
            )}

            <button
              className="primary-btn finish-btn"
              onClick={() => {
                onClose();
                onPaymentComplete();
              }}
            >
              <span>{result.paymentStatus === 'SUCCEEDED' ? 'Go to My Bookings' : 'Try Again'}</span>
              <ArrowRight size={18} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
