import React, { useEffect, useState } from 'react';
import type { BookingResponse, PaymentResponse } from '../types';
import { paymentService } from '../services/paymentService';
import { Clock, CheckCircle2, XCircle, ShieldCheck, AlertTriangle, ArrowRight, X } from 'lucide-react';

interface PaymentModalProps {
  booking: BookingResponse;
  onClose: () => void;
  onPaymentComplete: () => void;
  onShowToast: (msg: string, type: 'error' | 'success' | 'info') => void;
}

export const PaymentModal: React.FC<PaymentModalProps> = ({
  booking,
  onClose,
  onPaymentComplete,
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
        onShowToast('Payment succeeded! Your tickets are confirmed.', 'success');
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
                <Clock size={22} />
              </div>
              <div className="countdown-text">
                <span className="countdown-label">Seat Hold Expires In:</span>
                <span className="countdown-digits">{formatTimer(secondsLeft)}</span>
              </div>
              {secondsLeft === 0 && (
                <div className="expired-notice">
                  <AlertTriangle size={16} />
                  <span>Hold expired. Seats released back to public.</span>
                </div>
              )}
            </div>

            {/* Order Details */}
            <div className="order-summary-card">
              <div className="order-row">
                <span className="order-label">Selected Seats</span>
                <span className="order-value">
                  {booking.seats.map((s) => `${s.seatRow}${s.seatNumber}`).join(', ')}
                </span>
              </div>
              <div className="order-row">
                <span className="order-label">Showtime</span>
                <span className="order-value">
                  {new Date(booking.showStartTime).toLocaleString(undefined, {
                    weekday: 'short',
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
              <div className="order-divider" />
              <div className="order-row total-row">
                <span className="total-title">Total Due:</span>
                <span className="total-price">${Number(booking.totalAmount).toFixed(2)}</span>
              </div>
            </div>

            {/* Simulation Actions */}
            <div className="simulation-actions">
              <button
                className="simulate-btn success-btn"
                disabled={loading || secondsLeft === 0}
                onClick={() => handleSimulatePayment(true)}
              >
                <CheckCircle2 size={18} />
                <span>{loading ? 'Processing...' : 'Simulate Payment Success'}</span>
              </button>

              <button
                className="simulate-btn failure-btn"
                disabled={loading || secondsLeft === 0}
                onClick={() => handleSimulatePayment(false)}
              >
                <XCircle size={18} />
                <span>{loading ? 'Processing...' : 'Simulate Payment Failure'}</span>
              </button>
            </div>
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
              {result.paymentStatus === 'SUCCEEDED' ? 'Payment Confirmed!' : 'Payment Failed'}
            </h3>

            <p className="result-desc">
              {result.paymentStatus === 'SUCCEEDED'
                ? 'Your seats have been booked permanently. You can access your tickets anytime in My Bookings.'
                : 'The simulation marked this payment as failed. All held seats have been released back to the available pool.'}
            </p>

            <div className="receipt-box">
              <div className="receipt-row">
                <span>Transaction Ref:</span>
                <code>{result.transactionRef}</code>
              </div>
              <div className="receipt-row">
                <span>Booking Status:</span>
                <span className={`status-pill pill-${result.bookingStatus.toLowerCase()}`}>
                  {result.bookingStatus}
                </span>
              </div>
              <div className="receipt-row">
                <span>Amount:</span>
                <strong>${Number(result.amount).toFixed(2)}</strong>
              </div>
            </div>

            <button
              className="primary-btn finish-btn"
              onClick={() => {
                onClose();
                onPaymentComplete();
              }}
            >
              <span>View My Bookings</span>
              <ArrowRight size={18} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
